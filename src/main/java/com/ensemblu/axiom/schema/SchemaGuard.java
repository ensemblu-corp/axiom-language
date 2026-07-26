package com.ensemblu.axiom.schema;

import com.ensemblu.axiom.api.Axiom;
import com.ensemblu.axiom.core.validation.If;
import com.ensemblu.axiom.core.validation.Result;
import com.ensemblu.axiom.core.data_structure.map.PersistentMap;
import com.ensemblu.axiom.sovereign.parser.AxiomDopParser;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public interface SchemaGuard {

    ConcurrentHashMap<String, PersistentMap<String, Object>> REGISTRY = new ConcurrentHashMap<>();


    static void reloadAll() {
        REGISTRY.clear();
    }

    static void reload(String rawPath) {
        REGISTRY.remove(rawPath.contains(".") ? rawPath : rawPath + ".axiom");
    }

    interface BasedOnSchemaInPath {
        WithParser basedOnSchemaInPath(String path);
    }

    interface WithParser {
        default Result<PersistentMap<String, Object>> withAxiomParser() {
            return withParser(content -> AxiomDopParser.take(content).openBuffer().parse());
        }

        Result<PersistentMap<String, Object>> withParser(Function<String, PersistentMap<String, Object>> mapper);
    }

    static BasedOnSchemaInPath checkContent(String content) {
        return rawPath -> parser ->//
                If.givenObject(content)//
                        .isNonNull("Input")//
                        .andOtherObjectIsNotNull(rawPath, "Schema name")//
                        .will()//
                        .getResult()//
                        .flatMapTry(c -> {//
                            final var dataMap = parser.apply(content);//
                            return executeHandshake(dataMap,rawPath);//
                        })//
                        .nameThrowingPredicate(Throwable::getMessage);
    }

    private static Result<PersistentMap<String, Object>> executeHandshake(PersistentMap<String, Object> dataMap, String rawPath) {
        final var path = rawPath.endsWith(".axiom") ? rawPath : rawPath + ".axiom";

        return  Axiom.Check.attempt(() -> {
            final var schemaMap = REGISTRY.computeIfAbsent(path, SchemaGuard::loadSchemaFromDisk);

            return MasterGuard.validate(dataMap, schemaMap);
        }).flatMap(t -> t);
    }

    private static PersistentMap<String, Object> loadSchemaFromDisk(String path) {
        var loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) loader = SchemaGuard.class.getClassLoader();

        try (final var is = loader.getResourceAsStream(path)) {
            If.givenObject(is)//
                    .isNonNull()//
                    .will()//
                    .getValueOrElseThrow(//
                    () -> new RuntimeException(String.format("Axiom Schema [%s] missing", path)//
                    )//
            );//

            final var content = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            return AxiomDopParser.take(content).openBuffer().parse();

        } catch (Exception e) {
            throw new RuntimeException("Axiom Registry Load Failure: " + e.getMessage(), e);
        }
    }
}