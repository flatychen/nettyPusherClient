package cn.flaty.nio;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SimpleByteBuf implements ByteBuf{
	
	private static int BUFFER_SIZE = 256;

	private ByteBuffer buffer;

	public SimpleByteBuf() {
		super();
		this.buffer = ByteBuffer.allocate(BUFFER_SIZE);
	}
	
	
	public final SimpleByteBuf clear() {
		buffer.clear();
		return this;
	}

	public final SimpleByteBuf flip() {
		buffer.flip();
		return this;
	}

	public SimpleByteBuf  position(int i) {
		buffer.position(i);
		return this;
	}

	public int  position() {
		return buffer.position();
	}
	
	public SimpleByteBuf  limit(int i) {
		buffer.limit(i);
		return this;
	}

	public int  limit() {
		return buffer.limit();
	}
	
	public int  remaining() {
		return buffer.remaining();
	}

	public int  capacity() {
		return buffer.capacity();
	}

	public final SimpleByteBuf put(byte b[]) {
		buffer.put(b);
		return this;
	}

	public final SimpleByteBuf get(byte b[]) {
		buffer.get(b);
		return this;
	}
	
	



	@Override
	public int nioBufferSize() {
		return 1;
	}



	@Override
	public ByteBuffer nioBuffer() {
		return this.buffer;
	}



	
	@Override
	public ByteBuffer[] nioBuffers() {
		return new ByteBuffer[]{buffer};
	}

}
