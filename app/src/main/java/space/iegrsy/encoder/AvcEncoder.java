package space.iegrsy.encoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

public class AvcEncoder {
    private static final String TAG = AvcEncoder.class.getSimpleName();

    private static final int DEFAULT_FRAME_RATE = 15;
    private static final int DEFAULT_BIT_RATE = 500000; // 500 Kb/s
    private static final int TIMEOUT_U_SEC = 10000; // 10 ms

    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final int I_FRAME_INTERVAL = 1;

    private MediaCodec mediaCodec;
    private int yPlaneSize;
    private int cPlaneSize;
    private long frameIndex = 0;
    private byte[] spsPpsInfo = null;
    private byte[] yuv420 = null;
    private int frameRate;

    private ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    public AvcEncoder init(int width, int height) throws Exception {
        return init(width, height, DEFAULT_FRAME_RATE, DEFAULT_BIT_RATE);
    }

    public AvcEncoder init(int width, int height, int frameRate, int bitrate) throws Exception {
        mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);

        boolean isSupport = false;
        MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
        MediaCodecInfo.CodecCapabilities capabilities = Objects.requireNonNull(codecInfo).getCapabilitiesForType(MIME_TYPE);

        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int format = capabilities.colorFormats[i];
            if (format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                isSupport = true;
                break;
            }
        }
        if (!isSupport)
            throw new Exception("Is not supported.");

        this.yPlaneSize = width * height;
        this.cPlaneSize = yPlaneSize / 4;
        this.frameRate = frameRate;

        this.yuv420 = new byte[width * height * 3 / 2];

        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);

        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mediaCodec.start();

        Log.i(TAG, String.format("encoder setup: size[%s, %s], fps: %s, bitrate: %s", width, height, frameRate, bitrate));

        return this;
    }

    public void release() {
        try {
            mediaCodec.stop();
            mediaCodec.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public byte[] offerEncoder(byte[] input) {
        if (mediaCodec == null)
            throw new NullPointerException("Not create MediaCodec. Call init function.");

        YV12toYUV420PackedSemiPlanar(input, yuv420, yPlaneSize, cPlaneSize);
        try {
            int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
            if (inputBufferIndex >= 0) {
                long pts = computePresentationTime(frameIndex, frameRate);
                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
                Objects.requireNonNull(inputBuffer).clear();
                inputBuffer.put(yuv420);
                mediaCodec.queueInputBuffer(inputBufferIndex, 0, yuv420.length, pts, 0);
                frameIndex++;
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_U_SEC);

            while (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
                byte[] outData = new byte[bufferInfo.size];
                Objects.requireNonNull(outputBuffer).get(outData);

                if (spsPpsInfo == null) {
                    ByteBuffer spsPpsBuffer = ByteBuffer.wrap(outData);
                    if (spsPpsBuffer.getInt() == 0x00000001) {
                        spsPpsInfo = new byte[outData.length];
                        System.arraycopy(outData, 0, spsPpsInfo, 0, outData.length);
                    } else {
                        return null;
                    }
                } else {
                    outputStream.write(outData);
                }

                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_U_SEC);
            }
            byte[] ret = outputStream.toByteArray();
            if (ret.length > 5 && ret[4] == 0x65) { //key frame need to add sps pps
                outputStream.reset();
                outputStream.write(spsPpsInfo);
                outputStream.write(ret);
            }

        } catch (Throwable t) {
            t.printStackTrace();
        }
        byte[] ret = outputStream.toByteArray();
        outputStream.reset();
        return ret;
    }

    private void YV12toYUV420PackedSemiPlanar(final byte[] input, final byte[] output, final int yPlaneSize, final int cPlaneSize) {
        System.arraycopy(input, 0, output, 0, yPlaneSize);        // Y

        for (int i = 0; i < cPlaneSize; i++) {
            output[yPlaneSize + i * 2] = input[yPlaneSize + i + cPlaneSize];    // Cb (U)
            output[yPlaneSize + i * 2 + 1] = input[yPlaneSize + i];             // Cr (V)
        }
    }

    private static MediaCodecInfo selectCodec(String mimeType) {
        MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        for (MediaCodecInfo codecInfo : mediaCodecList.getCodecInfos()) {
            Log.i(TAG, "codec name: " + codecInfo.getName() + " support: " + Arrays.toString(codecInfo.getSupportedTypes()));

            if (!codecInfo.isEncoder())
                continue;

            String[] types = codecInfo.getSupportedTypes();
            for (String type : types)
                if (type.equalsIgnoreCase(mimeType))
                    return codecInfo;
        }
        return null;
    }

    private static long computePresentationTime(long frameIndex, int framerate) {
        return 132 + frameIndex * 1000000 / framerate;
    }
}
