package org.ironrhino.core.fs;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;

public interface FileStorage {

	int DEFAULT_PAGE_SIZE = 100;

	int MAX_PAGE_SIZE = 10000;

	Comparator<FileInfo> COMPARATOR = Comparator.comparing(FileInfo::isFile).thenComparing(FileInfo::getName);

	public default boolean isBucketBased() {
		try {
			return getClass().getMethod("getBucket") != null;
		} catch (NoSuchMethodException | SecurityException e) {
			return false;
		}
	}

	public default boolean isRelativeProtocolAllowed() {
		return isBucketBased();
	}

	public default void migrateTo(FileStorage target, String directory, boolean removeSourceFiles) throws IOException {
		if (directory == null)
			directory = "/";
		if (!directory.endsWith("/"))
			directory = directory + "/";
		boolean paging = this.isBucketBased();
		if (paging) {
			String marker = null;
			Paged<FileInfo> files = null;
			do {
				files = this.listFilesAndDirectory(directory, 100, marker);
				for (FileInfo entry : files.getResult()) {
					String path = directory + entry.getName();
					if (entry.isFile()) {
						target.write(this.open(path), path);
						if (removeSourceFiles)
							this.delete(path);
					} else {
						migrateTo(target, path, removeSourceFiles);
					}
				}
				marker = files.getNextMarker();
			} while (marker != null);
		} else {
			List<FileInfo> files = this.listFilesAndDirectory(directory);
			for (FileInfo entry : files) {
				String path = directory + entry.getName();
				if (entry.isFile()) {
					target.write(this.open(path), path);
					if (removeSourceFiles)
						this.delete(path);
				} else {
					migrateTo(target, path, removeSourceFiles);
				}
			}
		}
		if (removeSourceFiles && !directory.equals("/"))
			this.delete(directory);
	}

	public default void write(File file, String path) throws IOException {
		try (FileInputStream is = new FileInputStream(file)) {
			write(is, path, file.length());
		}
	}

	public default void write(InputStream is, String path, long contentLength) throws IOException {
		String contentType = null;
		int index = path.lastIndexOf('/');
		Optional<MediaType> type = MediaTypeFactory.getMediaType(index >= 0 ? path.substring(index + 1) : path);
		if (type.isPresent())
			contentType = type.get().toString();
		write(is, path, contentLength, contentType);
	}

	public default void write(InputStream is, String path, long contentLength, String contentType) throws IOException {
		write(is, path);
	}

	public void write(InputStream is, String path) throws IOException;

	public InputStream open(String path) throws IOException;

	public boolean mkdir(String path) throws IOException;

	public boolean delete(String path) throws IOException;

	public boolean exists(String path) throws IOException;

	public boolean rename(String fromPath, String toPath) throws IOException;

	public boolean isDirectory(String path) throws IOException;

	public long getLastModified(String path) throws IOException;

	public List<String> listFiles(String path) throws IOException;

	public default Paged<String> listFiles(String path, int limit, String marker) throws IOException {
		if (limit < 1 || limit > MAX_PAGE_SIZE)
			limit = DEFAULT_PAGE_SIZE;
		if (marker != null && marker.isEmpty())
			marker = null;
		List<String> files = listFiles(path);
		int start = marker == null ? 0 : files.indexOf(marker);
		if (start == -1)
			return new Paged<>(marker, null, Collections.emptyList());
		return new Paged<>(marker, start + limit < files.size() ? files.get(start + limit) : null,
				files.subList(start, Math.min(start + limit, files.size())));
	}

	public List<FileInfo> listFilesAndDirectory(String path) throws IOException;

	public default Paged<FileInfo> listFilesAndDirectory(String path, int limit, String marker) throws IOException {
		if (limit < 1 || limit > MAX_PAGE_SIZE)
			limit = 20;
		if (marker != null && marker.isEmpty())
			marker = null;
		List<FileInfo> files = listFilesAndDirectory(path);
		int start;
		if (marker == null) {
			start = 0;
		} else {
			start = -1;
			for (int i = 0; i < files.size(); i++) {
				if (files.get(i).getName().equals(marker))
					start = i;
			}
			if (start == -1)
				return new Paged<>(marker, null, Collections.emptyList());
		}
		return new Paged<>(marker, start + limit < files.size() ? files.get(start + limit).getName() : null,
				files.subList(start, Math.min(start + limit, files.size())));
	}

	public String getFileUrl(String path);

}
