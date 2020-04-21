package net.guerra24.hi3generator;

import static org.lwjgl.util.nfd.NativeFileDialog.NFD_OKAY;
import static org.lwjgl.util.nfd.NativeFileDialog.NFD_PickFolder;
import static org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_messageBox;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.lwjgl.system.MemoryStack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Generator {

	private static final byte[] VERSION = hexStringToByteArray(
			"35 2E 78 2E 78 00 32 30 31 37 2E 34 2E 31 38 66 31 00".replace(" ", ""));

	private static String INPUT_FOLDER = "";

	private static Map<Integer, Integer> ciBlockCount = new ConcurrentHashMap<>(),
			uiBlockCount = new ConcurrentHashMap<>();
	private static AtomicInteger progress = new AtomicInteger(0);
	private static int totalFiles;
	private static boolean running = true;

	public static void main(String[] args) {
		try (var stack = MemoryStack.stackPush()) {
			var inPath = stack.mallocPointer(1);
			tinyfd_messageBox("Generator", "Select input directory", "ok", "info", false);
			int resultIn = NFD_PickFolder((ByteBuffer) null, inPath);
			if (resultIn == NFD_OKAY)
				INPUT_FOLDER = inPath.getStringUTF8();
		}
		if (INPUT_FOLDER.isEmpty()) {
			tinyfd_messageBox("Generator", "No input directory selected", "ok", "error", false);
			return;
		}
		List<String> files = new ArrayList<>();
		var executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		try (Stream<Path> walk = Files.walk(Paths.get(INPUT_FOLDER))) {
			files.addAll(walk.map(x -> x.toString()).filter(f -> f.endsWith(".unity3d")).collect(Collectors.toList()));
		} catch (IOException e1) {
		}
		totalFiles = files.size();
		for (String file : files)
			executor.submit(() -> processFile(file));
		executor.shutdown();
		var t = new Thread(() -> {
			while (running) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e3) {
				}
				System.out.println(progress.get() + "/" + totalFiles);
			}
		});
		t.start();
		try {
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		running = false;
		Map<Integer, Integer> sortedCiBlock = ciBlockCount.entrySet().stream().sorted((a, b) -> {
			return -a.getValue().compareTo(b.getValue());
		}).collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue(), (e1, e2) -> e2, LinkedHashMap::new));
		Map<Integer, Integer> sortedUiBlock = uiBlockCount.entrySet().stream().sorted((a, b) -> {
			return -a.getValue().compareTo(b.getValue());
		}).collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue(), (e1, e2) -> e2, LinkedHashMap::new));
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try (var stack = MemoryStack.stackPush()) {
			var inPath = stack.mallocPointer(1);
			tinyfd_messageBox("Generator", "Select output directory", "ok", "info", false);
			int resultIn = NFD_PickFolder((ByteBuffer) null, inPath);
			if (resultIn == NFD_OKAY) {
				String outpath = inPath.getStringUTF8();
				try (var writer = new FileWriter(outpath + File.separator + "ciBlocks.json")) {
					gson.toJson(sortedCiBlock, writer);
				} catch (IOException e3) {
				}
				try (var writer = new FileWriter(outpath + File.separator + "uiBlocks.json")) {
					gson.toJson(sortedUiBlock, writer);
				} catch (IOException e3) {
				}
			}
			tinyfd_messageBox("Generator", "Completed", "ok", "info", false);
		}
	}

	private static void processFile(String file) {
		try (var raf = new RandomAccessFile(file, "r")) {
			MappedByteBuffer buffer = raf.getChannel().map(MapMode.READ_ONLY, 0, 64);
			byte[] header = new byte[8];
			buffer.get(header);
			int a = buffer.getInt();
			byte[] version = new byte[18];
			buffer.get(version);
			boolean valid = Arrays.equals(version, VERSION);
			if (!valid) {
				progress.incrementAndGet();
				return;
			}
			long size = buffer.getLong();
			int ciblock = buffer.getInt();
			int uiblock = buffer.getInt();
			int flags = buffer.getInt();
			var ciCount = ciBlockCount.get(ciblock);
			if (ciCount == null)
				ciCount = 0;
			ciCount++;
			ciBlockCount.put(ciblock, ciCount);
			var uiCount = uiBlockCount.get(uiblock);
			if (uiCount == null)
				uiCount = 0;
			uiCount++;
			uiBlockCount.put(uiblock, uiCount);
		} catch (IOException e) {
			e.printStackTrace();
		}
		progress.incrementAndGet();
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
