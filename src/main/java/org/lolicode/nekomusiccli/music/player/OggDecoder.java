package org.lolicode.nekomusiccli.music.player;

import org.lolicode.nekomusiccli.libs.ogg.oggdecoder.OggData;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class OggDecoder extends org.lolicode.nekomusiccli.libs.ogg.oggdecoder.OggDecoder implements Decoder {
    private final ByteArrayInputStream inputStream;
    private final OggData data;
    private volatile boolean consumed = false;
    public OggDecoder(ByteArrayInputStream inputStream) throws IOException {
        super();
        this.inputStream = inputStream;
        this.data = super.getData(inputStream);
    }

    @Override
    public int getOutputFrequency() {
        return data.rate;
    }

    @Override
    public int getOutputChannels() {
        return data.channels;
    }

    @Override
    public void close() throws Exception {
        inputStream.close();
    }

    @Override
    public synchronized ByteBuffer decodeFrame() throws Exception {
        if (consumed) {
            return null;
        }
        consumed = true;
        byte[] bytes = super.getConvbuffer();
        return Decoder.getByteBuffer(bytes);
    }
}
