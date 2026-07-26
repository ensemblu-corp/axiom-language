package com.ensemblu.axiom.schema;

import com.ensemblu.axiom.api.Axiom;
import com.ensemblu.axiom.core.foundation.Dop;
import com.ensemblu.axiom.core.navigation.Source;
import com.ensemblu.axiom.core.validation.If;
import com.ensemblu.axiom.core.validation.Result;
import com.ensemblu.axiom.core.data_structure.list.PersistentList;
import static com.ensemblu.axiom.core.data_structure.list.PersistentList.Accumulator;

import com.ensemblu.axiom.core.data_structure.map.PersistentMap;
import com.ensemblu.axiom.core.foundation.Nothing;
import com.ensemblu.axiom.core.foundation.TraversalBreak;

public final class MasterGuard {
    private static final int MAX_DEPTH = 32;

    private MasterGuard() {
        throw new AssertionError("MasterGuard: The constructor is sealed; structural integrity must be maintained.");
    }

    public static Result<PersistentMap<String, Object>> validate(
            PersistentMap<String, Object> data,
            PersistentMap<String, Object> schema
    ) {
        return walk(Source.of(data), schema, 0, "$").map(v -> data);
    }

    private static Result<Nothing> walk(Source source, PersistentMap<String, Object> rules, int depth, String path) {
        if (rules == null) return Axiom.Check.success(Nothing.INSTANCE);
        if (depth > MAX_DEPTH) return Axiom.Check.failure("Depth Guard Triggered at " + path);

        // 1. Structure first: Does the path exist and match the type?
        // 2. Constraints second: Now that we know it's a valid node, apply rules.
        return checkStructure(source, rules, depth, path)
                .flatMap(ok -> checkType(source, rules, path))
                .flatMap(ok -> checkConstraints(source, rules, path));


    }

    private static Result<Nothing> checkType(Source source, PersistentMap<String, Object> rules, String path) {
        final var type = rules.targetKey("type").toStringResult().getOrElse(() -> null);
        final var inferredType = (type != null) ? type :
                (rules.exists("properties")) ? "object" :
                (rules.exists("record"))      ? "array"  : null;

        if (inferredType == null) return Axiom.Check.failure("Missing type at " + path);

        return (switch (inferredType) {
            case "string"  -> source.navigate().toStringResult();
            case "integer" -> source.navigate().toLongResult();
            case "double"  -> source.navigate().toDoubleResult();
            case "boolean" -> source.navigate().toBooleanResult();
            case "object"  -> source.navigate().toMapProjectorResult();
            case "array"   -> source.navigate().toListProjectorResult();
            default        -> Result.failure("Unsupported type [" + inferredType + "] at " + path);
        }).mapFailure(e -> "Type Mismatch at " + path + ": " + e).mapEmpty();
    }

    private static Result<Nothing> checkConstraints(Source source, PersistentMap<String, Object> rules, String path) {
        final var val = source.getValue();
        if (val == null) return Axiom.Check.success(Nothing.INSTANCE);

        final var enums = rules.targetKey("enum").toListProjectorResult().map(Dop.ListProjector::deploy).getOrElse(() -> null);
        if (enums != null && enums.findFirst(e -> Dop.isEqual(e, val)).isFailure()) {
            return Axiom.Check.failure("Constraint Failure at " + path + ": Value [" + val + "] not in " + enums);
        }

        final var validatedValue =  switch (val) {
            case Integer i -> Validator.validateNumerical(i.longValue(), rules, path);
            case Long l    -> Validator.validateNumerical(l, rules, path);
            case Double d  -> Validator.validateNumerical(d, rules, path);
            case String s  -> Validator.validateString(s, rules, path);
            case PersistentList<?> list -> Validator.validateList(list, rules, path);
            default -> Nothing.INSTANCE;
        };


        return Axiom.Check.success(validatedValue);
    }

    private static Result<Nothing> checkStructure(Source source, PersistentMap<String, Object> rules, int depth, String path) {
        final var required = rules
                .targetKey("required").toListProjectorResult().map(Dop.ListProjector::deploy).getOrElse(() -> null);
            if (required != null) {
                final var requiredResult = required.foldUntil(
                        Axiom.Check.success(Nothing.INSTANCE),
                        (acc, key) -> source.follow(key.toString()).exists()
                                ? Accumulator.cont(Axiom.Check.success(Nothing.INSTANCE))
                                : Accumulator.stop(Axiom.Check.failure("Required Field Missing: [" + path + "." + key + "]"))
                );

                if (requiredResult.isFailure()) return requiredResult;
            }


        if (source.getValue() == null) return Axiom.Check.success(Nothing.INSTANCE);


        final var props = rules.targetKey("properties").toMapProjectorResult()//

                .map(mapProjector -> mapProjector.mapKeys(String::valueOf).deploy())
                //
                .getOrElse(() ->null);
        if (props != null && source.getValue() instanceof PersistentMap) {
            return diveIntoProperties(source, props, depth, path, rules);
        }

        final var items = rules.targetKey("record").toMapProjectorResult()//

                .map(mapProjector -> mapProjector.mapKeys(String::valueOf).deploy())
                //
        .getOrElse(() ->null);
        if (items != null && source.getValue() instanceof PersistentList) {
            return diveIntoItems(source, items, depth, path);
        }

        return Axiom.Check.success(Nothing.INSTANCE);
    }

    private static Result<Nothing> diveIntoItems(Source source, PersistentMap<String, Object> itemsSchema, int depth, String path) {
        final var list = (PersistentList<?>) source.getValue();
        for (var i = 0; i < list.size(); i++) {
            final var res = walk(source.inIndex(i), itemsSchema, depth + 1, path + "[" + i + "]");
            if (res.isFailure()) return res;
        }
        return Axiom.Check.success(Nothing.INSTANCE);
    }

    @SuppressWarnings("unchecked")
    private static Result<Nothing> diveIntoProperties(Source source, PersistentMap<String, Object> props, int depth, String path, PersistentMap<String, Object> rules) {

        if (rules.targetKey("strict").toBooleanResult().getOrElse(() ->false)) {
            final var dataMap = (PersistentMap<String, Object>) source.getValue();
            final var strictError = new Result[]{Axiom.Check.success(Nothing.INSTANCE)};
            dataMap.forEach((key, val) -> {
                if (!props.exists(key)) {
                    strictError[0] = Axiom.Check.failure("Strict Mode Failure at " + path + ": Unknown field [" + key + "]");
                }
            });
            if (strictError[0].isFailure()) return strictError[0];
        }

        final Result<Nothing>[] failure = new Result[]{Result.success(Nothing.INSTANCE)};
        try {
            props.forEach((key, val) -> {
                if (val instanceof PersistentMap<?, ?> subRules) {
                    final var res = walk(source.follow(key), (PersistentMap<String, Object>) subRules, depth + 1, path + "." + key);
                    if (res.isFailure()) {
                        failure[0] = res;
                        throw TraversalBreak.INSTANCE;
                    }
                }
            });
        } catch (TraversalBreak _) {
        }
        return failure[0];
    }

    private static class Validator {

        static Nothing validateNumerical(double num, PersistentMap<String, Object> rules, String path) {
            final var min = rules.targetKey("min").toDoubleResult().getOrElse(() -> null);
            final var max = rules.targetKey("max").toDoubleResult().getOrElse(() -> null);

            return If.givenObject(num)//
                    .is(n -> min == null || n >= min, "Constraint Failure at " + path + ": " + num + " < " + min)//
                    .andIs(n -> max == null || n <= max, "Constraint Failure at " + path + ": " + num + " > " + max)//
                    .will()//
                    .thenApprovedOrElseThrowException();
        }

        static Nothing validateString(String s, PersistentMap<String, Object> rules, String path) {
            final var minLen = rules.targetKey("minLength").toLongResult().getOrElse(() -> null);
            final var maxLen = rules.targetKey("maxLength").toLongResult().getOrElse(() -> null); // Added
            final var pattern = rules.targetKey("pattern").toStringResult().getOrElse(() -> null);

            return If.givenObject(s)//
                    .is(str -> minLen == null || str.length() >= minLen,//
                            "Constraint Failure at " + path + ": String too short (min: " + minLen + ")")//
                    .andIs(str -> maxLen == null || str.length() <= maxLen,//
                            "Constraint Failure at " + path + ": String too long (max: " + maxLen + ")") //
                    .andIs(str -> pattern == null || str.matches(pattern),//
                            "Constraint Failure at " + path + ": Does not match pattern: " + pattern)//
                    .will()
                    .thenApprovedOrElseThrowException();
        }

        static Nothing validateList(PersistentList<?> list, PersistentMap<String, Object> rules, String path) {
            final var minItems = rules.targetKey("minItems").toLongResult().getOrElse(() -> null);
            final var maxItems = rules.targetKey("maxItems").toLongResult().getOrElse(() -> null);

            return If.givenObject(list)//
                    .is(l -> minItems == null || l.size() >= minItems, "Constraint Failure at " + path + ": Size below minimum (" + minItems + ")")//
                    .andIs(l -> maxItems == null || l.size() <= maxItems, "Constraint Failure at " + path + ": Size above maximum (" + maxItems + ")")//
                    .will()//
                    .thenApprovedOrElseThrowException();
        }
    }
}