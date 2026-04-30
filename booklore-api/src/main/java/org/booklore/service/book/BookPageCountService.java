package org.booklore.service.book;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.booklore.exception.ApiError;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.repository.BookRepository;

import org.booklore.util.FileUtils;

import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.exception.RarException;


@Slf4j
@AllArgsConstructor
@Service
public class BookPageCountService {

    private final BookRepository bookRepository;

    public int extractPageCount(long bookId) throws IOException {
        try{
            BookEntity bookEntity = bookRepository.findById(bookId)
                    .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

            BookFileEntity primaryFile = bookEntity.getPrimaryBookFile();
            if (primaryFile == null) {
                throw ApiError.FILE_NOT_FOUND.createException(bookId);
            }
            Path filePath = Paths.get(FileUtils.getBookFullPath(bookEntity)).toAbsolutePath().normalize();

            if (!Files.exists(filePath)) {
                throw ApiError.FILE_NOT_FOUND.createException(filePath);
            }

            String fileName = filePath.toString().toLowerCase();

            if (fileName.endsWith(".pdf")) {
                return countPdfPages(filePath);
            } else if (fileName.endsWith(".cbz")) {
                return countCbzPages(filePath);
            } else if (fileName.endsWith(".cbr")) {
                return countCbrPages(filePath);
            }
            throw new IllegalArgumentException("Unsupported file type");

        } catch (Exception e) {
            log.error("Failed to get page count for book {}: {}", bookId, e.getMessage(), e);
            throw ApiError.PAGE_COUNT_FAIL.createException(bookId);
        }
    }

    private int countPdfPages(Path filePath) throws IOException {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            return document.getNumberOfPages();
        }
    }

    private int countCbzPages(Path filePath) throws IOException {
        try (ZipFile zipFile = new ZipFile(filePath.toFile(),  StandardCharsets.ISO_8859_1)) {
            return (int) zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> isImageFile(entry.getName()))
                    .count();
        }
    }

    private int countCbrPages(Path filePath) throws IOException {
        int count = 0;

        try (Archive archive = new Archive(new File(filePath.toString()))) {
            FileHeader fileHeader;
            while ((fileHeader = archive.nextFileHeader()) != null) {

                String fileName = fileHeader.getFileNameString();

                if (isImageFile(fileName)) {
                    count++;
                }
            }
            return count;
        }  catch (RarException e) {
            throw new IOException("Failed to read CBR archive", e);
        }


    }

    private boolean isImageFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".webp");
    }
}