package net.guerra24.hi3unpacker;

import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memRealloc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

public class Utils {

	public static ByteBuffer ioResourceToByteBuffer(String resource, int bufferSize) throws IOException {
		ByteBuffer buffer;

		String resourceFile = resource;

		File file = new File(resourceFile);
		if (file.isFile()) {
			try (FileInputStream fis = new FileInputStream(file)) {
				try (FileChannel fc = fis.getChannel()) {
					buffer = memAlloc((int) fc.size() + 1);
					while (fc.read(buffer) != -1)
						;
				}
			}
		} else {
			int size = 0;
			buffer = memAlloc(bufferSize);
			try (InputStream source = Utils.class.getClassLoader().getResourceAsStream(resource)) {
				if (source == null)
					throw new FileNotFoundException(resource);
				try (ReadableByteChannel rbc = Channels.newChannel(source)) {
					while (true) {
						int bytes = rbc.read(buffer);
						if (bytes == -1)
							break;
						size += bytes;
						if (!buffer.hasRemaining())
							buffer = memRealloc(buffer, size * 2);
					}
				}
			}
			buffer = memRealloc(buffer, size + 1);
		}
		buffer.put((byte) 0);
		buffer.flip();
		return buffer;
	}
}
