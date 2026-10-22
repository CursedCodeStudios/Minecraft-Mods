package dev.frydae.utilities;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import static org.junit.jupiter.api.Assertions.*;

/** Porting guard: injection targets and private shadows are not checked by javac. */
class MixinTargetsTest {
    @Test void everyConfiguredMixinStillMatchesTheTargetGame() throws Exception {
        try (var stream = getClass().getResourceAsStream("/fryutilities.mixins.json")) {
            var config = JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
            String prefix = config.get("package").getAsString().replace('.', '/') + "/";
            for (var name : config.getAsJsonArray("client")) {
                var mixin = read(prefix + name.getAsString());
                var annotation = annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations).stream()
                    .filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
                @SuppressWarnings("unchecked") var targets = (List<Type>) value(annotation, "value");
                for (var targetType : targets) check(mixin, read(targetType.getInternalName()));
            }
        }
    }
    private void check(ClassNode mixin, ClassNode target) throws Exception {
        for (var field : mixin.fields) {
            if (annotations(field.visibleAnnotations, field.invisibleAnnotations).stream()
                .anyMatch(a -> a.desc.endsWith("/Shadow;")))
                assertTrue(hasField(target, field.name, field.desc), mixin.name + " missing shadow " + field.name);
        }
        for (var method : mixin.methods) {
            for (var annotation : annotations(method.visibleAnnotations, method.invisibleAnnotations)) {
                if (annotation.desc.endsWith("/Accessor;")) {
                    String field = (String)value(annotation, "value");
                    assertTrue(hasField(target, field, Type.getReturnType(method.desc).getDescriptor()),
                        mixin.name + " missing accessor " + field);
                }
                if (!annotation.desc.endsWith("/Inject;")) continue;
                @SuppressWarnings("unchecked") var selectors = (List<String>) value(annotation, "method");
                for (String selector : selectors) {
                    int descriptor = selector.indexOf('(');
                    String name = descriptor < 0 ? selector : selector.substring(0, descriptor);
                    var candidates = target.methods.stream().filter(m -> m.name.equals(name)
                        && (descriptor < 0 || m.desc.equals(selector.substring(descriptor)))).toList();
                    assertFalse(candidates.isEmpty(), mixin.name + " missing injection " + selector);
                    Type[] handler = Type.getArgumentTypes(method.desc);
                    assertTrue(candidates.stream().anyMatch(candidate -> {
                        Type[] args = Type.getArgumentTypes(candidate.desc);
                        if (handler.length == 1) return true; // callback-only handler
                        if (args.length + 1 != handler.length) return false;
                        for (int i = 0; i < args.length; i++) if (!args[i].equals(handler[i])) return false;
                        return true;
                    }), mixin.name + " injection parameters changed: " + selector);
                }
            }
        }
    }
    private boolean hasField(ClassNode target, String name, String descriptor) throws Exception {
        if (target.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(descriptor))) return true;
        return target.superName != null && hasField(read(target.superName), name, descriptor);
    }
    private ClassNode read(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input, "Missing mixin target " + name);
            var node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
    private static List<AnnotationNode> annotations(List<AnnotationNode> first, List<AnnotationNode> second) {
        List<AnnotationNode> result = new ArrayList<>();
        if (first != null) result.addAll(first);
        if (second != null) result.addAll(second);
        return result;
    }
    private static Object value(AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2)
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }
}
