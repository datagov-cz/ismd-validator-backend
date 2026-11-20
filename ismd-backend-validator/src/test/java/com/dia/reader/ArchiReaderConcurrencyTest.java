package com.dia.reader;

import com.dia.conversion.data.ClassData;
import com.dia.conversion.data.OntologyData;
import com.dia.conversion.reader.archi.ArchiReader;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/** One shared ArchiReader (as the Spring singleton is) reading different files at once gives each its own result. */
class ArchiReaderConcurrencyTest {

    private static final String DIR = "com/dia/canonical/complete/";

    @Test
    void parallelReadsOfDifferentFiles_doNotInterfere() throws Exception {
        ArchiReader reader = new ArchiReader();
        List<String> files = List.of(read("testArchiInput_no-lkod.xml"), read("ciselniky-test-Archi.xml"));
        List<String> expected = files.stream().map(file -> summary(readQuietly(reader, file))).toList();
        assertThat(expected.get(0)).isNotEqualTo(expected.get(1));

        int rounds = 40;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < rounds; i++) {
                String file = files.get(i % 2);
                results.add(pool.submit(() -> {
                    start.await();
                    return summary(reader.readArchiFromString(file));
                }));
            }
            start.countDown();
            for (int i = 0; i < rounds; i++) {
                assertThat(results.get(i).get(60, TimeUnit.SECONDS)).isEqualTo(expected.get(i % 2));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** What a cross-request leak would change: vocabulary, concepts and their mapped properties. */
    private static String summary(OntologyData data) {
        StringBuilder out = new StringBuilder(String.valueOf(data.getVocabularyMetadata().getName()));
        data.getClasses().stream().map(ClassData::getName).sorted().forEach(n -> out.append('|').append(n));
        data.getProperties().stream().map(p -> p.getName() + "@" + p.getDomain() + "#" + p.getDefinition())
                .sorted().forEach(n -> out.append('|').append(n));
        data.getClasses().stream().map(c -> c.getName() + "=" + c.getCodelistIri() + "/" + c.getDefinition())
                .sorted().forEach(n -> out.append('|').append(n));
        out.append("|rel=").append(data.getRelationships().size());
        return out.toString();
    }

    private static OntologyData readQuietly(ArchiReader reader, String file) {
        try {
            return reader.readArchiFromString(file);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String read(String name) throws Exception {
        try (var in = new ClassPathResource(DIR + name).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
