package org.lolicode.nekomusiccli.music.player;

import javazoom.jl.decoder.*;
import org.lolicode.nekomusiccli.libs.flac.decode.DataFormatException;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;

public class Mp3Decoder extends javazoom.jl.decoder.Decoder implements Decoder {
    private final Bitstream bitstream;
    public Mp3Decoder(ByteArrayInputStream inputStream) throws BitstreamException, DataFormatException {
        super();
        this.bitstream = new Bitstream(inputStream);
        var header = bitstream.readFrame();
        if (header == null) {
            throw new DataFormatException("Failed to read header");
        }
        bitstream.closeFrame();
    }

    @Override
    public synchronized void close() throws Exception {
        bitstream.close();
    }

    @Override
    public synchronized ByteBuffer decodeFrame() throws Exception {
        var frame = bitstream.readFrame();
        if (frame == null) {
            return null;
        }
        SampleBuffer buffer = (SampleBuffer) super.decodeFrame(frame, bitstream);
        ByteBuffer byteBuffer = Decoder.getByteBuffer(buffer.getBuffer());
        bitstream.closeFrame();
        return byteBuffer;
    }
}