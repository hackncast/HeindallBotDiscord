package com.DiscordEcho.Listeners;

import com.DiscordEcho.DiscordEcho;
import net.dv8tion.jda.core.audio.AudioReceiveHandler;
import net.dv8tion.jda.core.audio.CombinedAudio;
import net.dv8tion.jda.core.audio.UserAudio;

import java.util.Arrays;

public class AudioReceiveListener implements AudioReceiveHandler
{
    public static final double STARTING_MB = 0.5;
    public static final int CAP_MB = 16;
    public static final double PCM_MINS = 2;
    public boolean canReceive = true;
    public double volume = 1.0;

    public byte[] uncompVoiceData = new byte[(int) (3840 * 50 * 60 * PCM_MINS)]; //3840bytes/array * 50arrays/sec * 60sec = 1 mins
    public int uncompIndex = 0;

    public byte[] compVoiceData = new byte[(int) (1024 * 1024 * STARTING_MB)];    //start with 0.5 MB
    public int compIndex = 0;

    public boolean overwriting = false;

    public AudioReceiveListener(double volume) {
        this.volume = volume;
    }

    @Override
    public boolean canReceiveCombined()
    {
        return canReceive;
    }

    @Override
    public boolean canReceiveUser()
    {
        return false;
    }

    @Override
    public void handleCombinedAudio(CombinedAudio combinedAudio)
    {
        if (uncompIndex == uncompVoiceData.length / 2 || uncompIndex == uncompVoiceData.length) {
            new Thread(() -> {

                if (uncompIndex < uncompVoiceData.length / 2)  //first half
                    addCompVoiceData((Arrays.copyOfRange(uncompVoiceData, 0, uncompVoiceData.length / 2)));
                else
                    addCompVoiceData((Arrays.copyOfRange(uncompVoiceData, uncompVoiceData.length / 2, uncompVoiceData.length )));

            }).start();

            if (uncompIndex == uncompVoiceData.length)
                uncompIndex = 0;
        }

        for (byte b : combinedAudio.getAudioData(volume)) {
            uncompVoiceData[uncompIndex++] = b;
        }
    }

    public byte[] getVoiceData() {
        canReceive = false;

        //flush remaining audio
        byte[] remaining = new byte[uncompIndex];

        int start = uncompIndex < uncompVoiceData.length / 2 ? 0 : uncompVoiceData.length / 2;

        for (int i = 0; i < uncompIndex - start; i++) {
            remaining[i] = uncompVoiceData[start + i];
        }

        addCompVoiceData(DiscordEcho.encodePcmToMp3(remaining));

        byte[] orderedVoiceData;
        if (overwriting) {
            orderedVoiceData = new byte[compVoiceData.length];
        } else {
            orderedVoiceData = new byte[compIndex + 1];
            compIndex = 0;
        }

        for (int i=0; i < orderedVoiceData.length; i++) {
            if (compIndex + i < orderedVoiceData.length)
                orderedVoiceData[i] = compVoiceData[compIndex + i];
            else
                orderedVoiceData[i] = compVoiceData[compIndex + i - orderedVoiceData.length];
        }

        wipeMemory();
        canReceive = true;

        return orderedVoiceData;
    }


    public void addCompVoiceData(byte[] compressed) {
        for (byte b : compressed) {
            if (compIndex >= compVoiceData.length && compVoiceData.length != 1024 * 1024 * CAP_MB) {    //cap at 16MB

                byte[] temp = new byte[compVoiceData.length * 2];
                for (int i=0; i < compVoiceData.length; i++)
                    temp[i] = compVoiceData[i];

                compVoiceData = temp;

            } else if (compIndex >= compVoiceData.length && compVoiceData.length == 1024 * 1024 * CAP_MB) {
                compIndex = 0;

                if (!overwriting) {
                    overwriting = true;
                    System.out.println("Hit compressed storage cap on a server");
                }
            }


            compVoiceData[compIndex++] = b;
        }
    }


    public void wipeMemory() {
        System.out.println("Wiped recording data");
        uncompIndex = 0;
        compIndex = 0;

        compVoiceData = new byte[1024 * 1024 / 2];
        System.gc();
    }


    public byte[] getUncompVoice(int time) {
        canReceive = false;

        if (time > PCM_MINS * 60 * 2) {     //2 mins
            time = (int)(PCM_MINS * 60 * 2);
        }
        int requestSize = 3840 * 50 * time;
        byte[] voiceData = new byte[requestSize];

        for (int i = 0; i < voiceData.length; i++) {
            if (uncompIndex + i < voiceData.length)
                voiceData[i] = uncompVoiceData[uncompIndex + i];
            else
                voiceData[i] = uncompVoiceData[uncompIndex + i - voiceData.length];
        }

        wipeMemory();
        canReceive = true;
        return voiceData;
    }

    @Override
    public void handleUserAudio(UserAudio userAudio) {}
}