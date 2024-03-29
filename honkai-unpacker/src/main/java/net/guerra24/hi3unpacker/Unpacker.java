package net.guerra24.hi3unpacker;

import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.util.nfd.NativeFileDialog.NFD_OKAY;
import static org.lwjgl.util.nfd.NativeFileDialog.NFD_PickFolder;
import static org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_messageBox;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.lwjgl.system.MemoryStack;

public class Unpacker {

	private static final byte[] HEADER = hexStringToByteArray(
			"55 6E 69 74 79 46 53 00 00 00 00 06 35 2E 78 2E 78 00 32 30 31 37 2E 34 2E 31 38 66 31 00".replace(" ",
					""));
	private static final byte[] VERSION = hexStringToByteArray(
			"35 2E 78 2E 78 00 32 30 31 37 2E 34 2E 31 38 66 31 00".replace(" ", ""));

	private static final byte[] CI_BLOCK = hexStringToByteArray("FF FF FF FF".replace(" ", ""));
	private static final byte[] UI_BLOCK = hexStringToByteArray("00 00 10 00".replace(" ", ""));
	private static final byte[] FLAGS = hexStringToByteArray("00 00 00 43".replace(" ", ""));

	private static String OUTPUT_FOLDER = "", INPUT_FOLDER = "";

	public static void main(String[] args) {
		try (var stack = MemoryStack.stackPush()) {
			var inPath = stack.mallocPointer(1);
			var outPath = stack.mallocPointer(1);
			tinyfd_messageBox("Unpacker", "Select input directory", "ok", "info", false);
			int resultIn = NFD_PickFolder((ByteBuffer) null, inPath);
			tinyfd_messageBox("Unpacker", "Select output directory", "ok", "info", false);
			int resultOut = NFD_PickFolder((ByteBuffer) null, outPath);
			if (resultIn == NFD_OKAY)
				INPUT_FOLDER = inPath.getStringUTF8();
			if (resultOut == NFD_OKAY)
				OUTPUT_FOLDER = outPath.getStringUTF8();
		}
		if (OUTPUT_FOLDER.isBlank() || INPUT_FOLDER.isEmpty()) {
			tinyfd_messageBox("Unpacker", "No input or output directory selected", "ok", "error", false);
			return;
		}

		File output = new File(OUTPUT_FOLDER);
		output.mkdirs();
		var executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		{
			File[] bundles = new File(INPUT_FOLDER).listFiles((pathname) -> {
				return pathname.getName().endsWith(".wmv");
			});
			for (File file : bundles)
				executor.submit(() -> processFile(file.getPath()));
		}
		{
			File[] u3ds = new File(INPUT_FOLDER).listFiles((pathname) -> {
				return pathname.getName().endsWith(".unity3d");
			});
			for (File file : u3ds)
				executor.submit(() -> processFile3D(file.getPath()));
		}
		executor.shutdown();
		try {
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		tinyfd_messageBox("Unpacker", "Unpacking completed", "ok", "info", false);
	}

	private static void processFile(String bundle) {
		try {
			List<ByteBuffer> outputFiles = new ArrayList<>();
			System.out.println(bundle);

			try {
				var file = Utils.ioResourceToByteBuffer(bundle, 1024 * 1024);
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
							byte[] version = new byte[18];
							// Read version
							for (int i = 0; i < 18; i++)
								version[i] = file.get(fileStart + i + 12);
							boolean valid = Arrays.equals(version, VERSION); // Check if metadata has been obfuscated
							long size = fileEnd - fileStart; // Calculate size
							int metaOffset = 0;
							if (!valid) {
								// Search for 1E 00 01 00
								for (int i = 0; i < 256; i++) {
									byte a = file.get(fileStart + i);
									byte b = file.get(fileStart + i + 1);
									byte c = file.get(fileStart + i + 2);
									byte d = file.get(fileStart + i + 3);
									if (Byte.compareUnsigned(a, (byte) 30) == 0
											&& Byte.compareUnsigned(b, (byte) 0) == 0
											&& Byte.compareUnsigned(c, (byte) 1) == 0
											&& Byte.compareUnsigned(d, (byte) 0) == 0) {
										metaOffset = i;
										break;
									}
								}
								fileStart += metaOffset; // Move until metaOffset
								size -= metaOffset - 50;
							}
							byte[] out = new byte[fileEnd - fileStart];
							for (int i = 0; i < fileEnd - fileStart; i++)
								out[i] = file.get(fileStart + i); // Fill array for easy work
							ByteBuffer buff = memAlloc(fileEnd - fileStart + (!valid ? 50 : 0));
							if (!valid) {

								// Search for CAB header
								int cabStart = 0;
								for (int i = 4; i < out.length; i++)
									if (Byte.compareUnsigned(out[i], (byte) 67) == 0
											&& Byte.compareUnsigned(out[i + 1], (byte) 65) == 0
											&& Byte.compareUnsigned(out[i + 2], (byte) 66) == 0) {
										cabStart = i;
										break;
									}
								// Search null terminator
								int cabEnd = 4;
								for (int i = cabStart; i < out.length; i++)
									if (Byte.compareUnsigned(out[i], (byte) 0) == 0) {
										cabEnd = i;
										break;
									}
								{
									// Check if there is garbage data in the filename
									// Go until 60, all data inside is garbage
									/*
									 * int cabGarbageEnd = 0; for (int i = cabEnd; i < out.length; i++) if
									 * (Byte.compareUnsigned(out[i], (byte) 96) == 0) { // Found garbage end
									 * cabGarbageEnd = i + 1; break; } else if (Byte.compareUnsigned(out[i], (byte)
									 * 240) == 0 && Byte.compareUnsigned(out[i + 1], (byte) 1) == 0) { // Found
									 * metadata marker cabGarbageEnd = -1; break; } //
									 * System.out.println(cabGarbageEnd); // remove garbage if (cabGarbageEnd > 0) {
									 * for (int i = cabEnd; i < cabGarbageEnd; i++) out = ArrayUtils.remove(out,
									 * cabEnd); size -= cabGarbageEnd - cabEnd; }
									 */
								}
								{
									// Check if there is garbage data after filename
								}
								/*
								 * int status = 0; for (int i = 4; i < out.length; i++) { if
								 * (Byte.compareUnsigned(out[i], (byte) 0) == 0 && Byte.compareUnsigned(out[i +
								 * 1], (byte) 109) == 0 && Byte.compareUnsigned(out[i + 2], (byte) 114) == 0 &&
								 * Byte.compareUnsigned(out[i + 3], (byte) 48) == 0 &&
								 * Byte.compareUnsigned(out[i + 4], (byte) 107) == 0) { status = 1; break; } }
								 * switch (status) { case 0: // Fine break; case 1: // Found garbage mark break;
								 * }
								 */
								int ciBlock = 0;
								for (int i = cabEnd; i < out.length; i++) {
									if (Byte.compareUnsigned(out[i], (byte) 240) == 0) {
										ciBlock = i;
										break;
									}
								}
								buff.put(HEADER);
								buff.put(hexStringToByteArray(String.format("%016X", size)));
								buff.put(hexStringToByteArray(String.format("%08X", ciBlock)));
								buff.put(UI_BLOCK);
								buff.put(FLAGS);
							}
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
			String directory = OUTPUT_FOLDER
					+ bundle.substring(bundle.lastIndexOf(File.separator), bundle.lastIndexOf("."));
			File outDir = new File(directory);
			if (!outputFiles.isEmpty())
				outDir.mkdir();
			for (int i = 0; i < outputFiles.size(); i++) {
				var buf = outputFiles.get(i);
				File file = new File(directory + File.separator + i + ".unity3d");
				try (var channel = new FileOutputStream(file).getChannel()) {
					channel.write(buf);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			for (var buf : outputFiles)
				memFree(buf);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void processFile3D(String bundle) {
		try {
			System.out.println(bundle);
			var file = Utils.ioResourceToByteBuffer(bundle, 1024 * 1024);
			int start = 0, end = file.remaining();

			int fileStart = start, fileEnd = end;
			byte[] version = new byte[18];
			for (int i = 0; i < 18; i++)
				version[i] = file.get(fileStart + i + 12);
			boolean valid = Arrays.equals(version, VERSION);
			long size = fileEnd - fileStart;
			if (!valid)
				fileStart += 50;
			byte[] out = new byte[fileEnd - fileStart];
			for (int i = 0; i < fileEnd - fileStart; i++)
				out[i] = file.get(fileStart + i);
			ByteBuffer buff = memAlloc(fileEnd - fileStart + (!valid ? 50 : 0));
			if (!valid) {
				int ciBlock = 0;
				for (int i = 4; i < out.length; i++) {
					if (Byte.compareUnsigned(out[i], (byte) 240) == 0
							&& Byte.compareUnsigned(out[i + 1], (byte) 1) == 0) {
						ciBlock = i;
						break;
					}
				}
				buff.put(HEADER);
				buff.put(hexStringToByteArray(String.format("%016X", size)));
				buff.put(hexStringToByteArray(String.format("%08X", ciBlock)));
				buff.put(UI_BLOCK);
				buff.put(FLAGS);
			}
			buff.put(out);
			buff.flip();
			memFree(file);
			File f = new File(OUTPUT_FOLDER + File.separator
					+ bundle.substring(bundle.lastIndexOf(File.separator), bundle.lastIndexOf(".")) + ".unity3d");
			try (var channel = new FileOutputStream(f).getChannel()) {
				channel.write(buff);
			} catch (IOException e) {
				e.printStackTrace();
			}
			memFree(buff);
		} catch (Exception e) {
			e.printStackTrace();
		}
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
