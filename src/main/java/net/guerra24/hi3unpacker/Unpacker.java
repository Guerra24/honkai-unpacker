package net.guerra24.hi3unpacker;

import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Unpacker {

	private static final byte[] HEADER = hexStringToByteArray(
			"55 6E 69 74 79 46 53 00 00 00 00 06 35 2E 78 2E 78 00 32 30 31 37 2E 34 2E 31 38 66 31 00 00 00 00 00 00"
					.replace(" ", ""));

	public static void main(String[] args) {
		File output = new File("./output");
		output.mkdirs();
		var executor = Executors.newFixedThreadPool(2);
		File[] bundles = new File("./input").listFiles((pathname) -> {
			return pathname.getName().endsWith(".wmv");
		});
		for (File file : bundles)
			executor.submit(() -> processFile(file.getPath()));
		executor.shutdown();
		try {
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private static void processFile(String bundle) {
		List<ByteBuffer> outputFiles = new ArrayList<>();
		System.out.println(bundle);

		try {
			var file = Utils.ioResourceToByteBuffer(bundle, 1024);
			int start = 0, end = 0;
			String format = "";
			while (file.hasRemaining()) {
				for (int i = 0; i < 7; i++) {
					format += new String(new byte[] { file.get() });
					if (!"UnityFS".startsWith(format)) {
						format = "";
						break;
					}
				}
				if (format.equals("UnityFS") || !file.hasRemaining()) {
					if (end != 0)
						start = end;
					end = file.position() - 7;
					format = "";
					if (end > 0) {
						int fileStart = start, fileEnd = end;
						boolean valid = file.get(fileStart + 6) == 83 && file.get(fileStart + 11) == 6;
						if (!valid)
							continue;
						if (!valid)
							fileStart += 8;
						byte[] out = new byte[fileEnd - fileStart];
						for (int i = 0; i < fileEnd - fileStart; i++)
							out[i] = file.get(fileStart + i);
						ByteBuffer buff = memAlloc(fileEnd - fileStart + (!valid ? HEADER.length : 0));
						if (!valid)
							buff.put(HEADER);
						buff.put(out);
						buff.flip();
						outputFiles.add(buff);
					}
				}
			}
			memFree(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
		String outPathDir = bundle.replace(".wmv", "/").replace("input", "output");
		File outDir = new File(outPathDir);
		if (!outputFiles.isEmpty())
			outDir.mkdir();
		for (int i = 0; i < outputFiles.size(); i++) {
			var buf = outputFiles.get(i);
			File file = new File(outPathDir + i + ".unity3d");
			try (var channel = new FileOutputStream(file).getChannel()) {
				channel.write(buf);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		for (var buf : outputFiles)
			memFree(buf);
	}

	public static byte[] hexStringToByteArray(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
		}
		return data;
	}
}
