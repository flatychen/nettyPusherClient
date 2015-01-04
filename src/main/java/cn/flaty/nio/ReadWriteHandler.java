package cn.flaty.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.flaty.pushFrame.FrameHead;
import cn.flaty.pushFrame.SimplePushFrame;
import cn.flaty.pushFrame.SimplePushHead;
import cn.flaty.services.PushService;
import cn.flaty.utils.ByteUtil;
import cn.flaty.utils.CharsetUtil;

public class ReadWriteHandler {
	
	private Logger log = LoggerFactory.getLogger(ReadWriteHandler.class);
	
	private static int BUFFER_SIZE = 4096;
	
	
	
	private FrameHead frameHeader = null;
	
	private ByteBuffer readBuf = ByteBuffer.allocate(BUFFER_SIZE);
	
	private ByteBuffer writeBuf = ByteBuffer.allocate(BUFFER_SIZE);
	
	
	/**
	 * 选择器，用于注册
	 */
	private Selector selector;
	
	/**
	 * socke通道
	 */
	private SocketChannel channel;
	
	/**
	 * 业务逻辑处理
	 */
	private PushService pushService ;
	
	/**
	 * nio 事件循环
	 */
	private SimpleEventLoop eventLoop;
	
	public ReadWriteHandler(PushService pushService) {
		this(pushService,new SimplePushHead());
	}
	

	public ReadWriteHandler(PushService pushService,FrameHead frameHeader) {
		super();
		this.pushService = pushService;
		this.frameHeader = frameHeader;
		
	}
	
	public void startConn(String host,int port){
		eventLoop = new SimpleEventLoop(host,port);
		try {
			eventLoop.setAccept(new AcceptHandler());
			eventLoop.setReadWrite(this);
			eventLoop.openChannel();
			this.selector = eventLoop.getSelector();
			
			// 阻塞等待
			new Runnable() {
				@Override
				public void run() {
					try {
						eventLoop.openChannel();
						eventLoop.connect();
					} catch (IOException e) {
						e.printStackTrace();
					}
					
				}
			};
			
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	
	public void writeMsg(String msg){
		try {
			channel.register(selector, SelectionKey.OP_WRITE);
			byte [] frame = encodeFrame(msg);
			writeBuf.put(frame);
		} catch (ClosedChannelException e) {
			e.printStackTrace();
		}
	}
	
	
	public void doWrite(SelectionKey key) {
		this.initChannel(key);
		writeBuf.flip();
		if( writeBuf.remaining() == 0 ){
			log.warn("----> writeBuf 无内容发送 ");
			return ;
		}
		try {
			int r = channel.write(writeBuf);
		} catch (IOException e) {
			e.printStackTrace();
		}
		writeBuf.clear();
	}
	
	
	private byte[] encodeFrame(String s){
		byte[] body = s.getBytes();
		int bodyLength = body.length;
		byte[] frame = new byte[bodyLength + frameHeader.byteLength() + frameHeader.headLength()];
		
		
		// 添加长度
		byte [] header_len = ByteUtil.intToByteArray(bodyLength);
		System.arraycopy(header_len, 0, frame, 0, frameHeader.byteLength() );
		
		
		// 添加帧头，4字节
		byte [] header = new byte[frameHeader.headLength()];
		// utf 编码
		header[1] = 1; 
		System.arraycopy(header, 0, frame, frameHeader.byteLength() , frameHeader.headLength());
	
		// 添加内容
		System.arraycopy(body, 0, frame, frameHeader.byteLength() + frameHeader.headLength(), bodyLength);
		
		return frame;
	}
	
	public void doRead( SelectionKey key) throws IOException {
		
		this.initChannel(key);
		
		readBuf.clear();
		if(channel.read(readBuf) == -1 ){
			throw new IOException("----> 读取失败");
		}
		
		// 切包
		byte [] frameBytes = this.splitFrame();
		SimplePushFrame frame = new SimplePushFrame(frameHeader, frameBytes);
		
		// 解包，得到内容
		String s = this.dencodeFrame(frame);
		
		pushService.receiveMsg(s);
		
		channel.register(selector, SelectionKey.OP_READ);
	}



	private String dencodeFrame(SimplePushFrame frame) {
		byte charsetType = frame.getCharsetType();
		String s = null;
		byte [] _b = frame.getBody();
		switch (charsetType) {
		case 0:
			s = new String(_b);
			break;
		case 1:
			s = new String(_b , CharsetUtil.UTF_8);
			break;
		case 2:
			s = new String(_b , CharsetUtil.US_ASCII);
			break;
		case 3:
			s = new String(_b , CharsetUtil.GBK);
			break;
		case 4:
			s = new String(_b , CharsetUtil.GB2312);
			break;
		}
		return s;
	}


	private void initChannel(SelectionKey key){
		if(channel == null){
			this.channel = (SocketChannel)key.channel();
		}
	}
	
	
	
	/**
	 * 
	 * FIXME 扩展buf
	 * @return
	 */
	private byte[] splitFrame(){
		readBuf.flip();
		
		// 
		// 拆帧开始;
		// 如果读取的字节大于帧头长度，则一直等到长度为止，
		// 否则，一直读等到帧头长度大于为止;
		// 
		int bytesToRead = 0; 
		
		if( readBuf.remaining() > frameHeader.byteLength() ){
		// 	必须读到帧的长度字节
			
			byte [] frameLengthBytes = new byte[frameHeader.byteLength()];
			readBuf.get(frameLengthBytes);
			// 帧长度大小
			bytesToRead = frameHeader.byteLength();
			
			if( readBuf.remaining() < bytesToRead ){
			// 没有完整读到所有帧的内容,应继续读取	
				
				if(readBuf.limit() == readBuf.capacity() ){
				// buffer 长度不够 ，需扩展buffer
					
					// 注意后期抽象
					int oldBufLength = readBuf.capacity();
					int newBufLength = bytesToRead + frameHeader.byteLength() ;
					ByteBuffer newBuffer = ByteBuffer.allocateDirect(newBufLength);
					
					readBuf.position(0);
					newBuffer.put(readBuf);
					
					readBuf = newBuffer;
					
					readBuf.position(oldBufLength); 
					readBuf.limit(newBufLength); 
					
					return null;
				}
				
			}
			
			
		}else{
			//
			// 没读到帧长度字节，调整buffer，继续读取
			// 注意postion 与 limit 设置
			// 接上次位置，继续读取
			// 
			readBuf.position(readBuf.limit());
			readBuf.limit(readBuf.capacity());
			return null;
		}
		
		// 拆帧完毕
		
		byte [] frame  =  new byte[bytesToRead];
		readBuf.get(frame);
		return frame;
		
		
	}
	
}
