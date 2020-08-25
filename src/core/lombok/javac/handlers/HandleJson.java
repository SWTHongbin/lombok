package lombok.javac.handlers;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.util.List;
import lombok.JsonSerializable;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import org.mangosdk.spi.ProviderFor;

import static lombok.javac.handlers.JavacHandlerUtil.*;

/**
 * @author lihongbin
 * Handles the {@code lombok.JsonSerializable} annotation for javac.
 */
@ProviderFor(JavacAnnotationHandler.class)
public class HandleJson extends JavacAnnotationHandler<JsonSerializable> {
    private static final String TO_JSON_FIELD_NAME = "toJson",
            FROM_JSON_FIELD_NAME = "fromJson";

    @Override
    public void handle(AnnotationValues<JsonSerializable> annotation, JCAnnotation ast, JavacNode annotationNode) {
        deleteAnnotationIfNeccessary(annotationNode, JsonSerializable.class);
        JavacNode typeNode = annotationNode.up();
        if (!isClass(typeNode)) {
            annotationNode.addError("@JsonSerializable is only supported on a class.");
            return;
        }
        boolean notAClass = true;
        if (typeNode.get() instanceof JCTree.JCClassDecl) {
            long flags = ((JCTree.JCClassDecl) typeNode.get()).mods.flags;
            notAClass = (flags & (Flags.INTERFACE | Flags.ANNOTATION)) != 0;
        }

        if (notAClass) {
            annotationNode.addError("@JsonSerializable is only supported on a class.");
            return;
        }

        if (methodExists(TO_JSON_FIELD_NAME, typeNode, -1) != MemberExistsResult.NOT_EXISTS) {
            annotationNode.addWarning("Field '" + TO_JSON_FIELD_NAME + "' already exists.");
        } else {
            JCTree.JCMethodDecl method = createToJsonMethod(typeNode, annotationNode.get());
            injectMethod(typeNode, method);
        }
        if (methodExists(FROM_JSON_FIELD_NAME, typeNode, 1) != MemberExistsResult.NOT_EXISTS) {
            annotationNode.addWarning("Field '" + FROM_JSON_FIELD_NAME + "' already exists.");
        } else {
            JCTree.JCMethodDecl method = createFromJsonMethod(typeNode, annotationNode.get());
            injectMethod(typeNode, method);
        }

    }

    private JCTree.JCMethodDecl createFromJsonMethod(JavacNode typeNode, JCTree jcTree) {
        lombok.javac.JavacTreeMaker maker = typeNode.getTreeMaker();
        JCTree.JCModifiers mods = maker.Modifiers(Flags.PUBLIC | Flags.STATIC);
        JCTree.JCExpression jcExpression = chainDotsString(typeNode, "java.lang.System.currentTimeMillis");
        List<JCTree.JCExpression> aThis = List.<JCTree.JCExpression>of(maker.Ident(typeNode.toName("this")));
        JCTree.JCMethodInvocation memberAccessor = maker.Apply(List.<JCTree.JCExpression>nil(), jcExpression, List.<JCTree.JCExpression>nil());
        JCTree.JCStatement statements = maker.Return(memberAccessor);
        JCTree.JCBlock body = maker.Block(0, List.of(statements));

        JCTree.JCMethodDecl methodDef = maker.MethodDef(
                mods,
                typeNode.toName(FROM_JSON_FIELD_NAME),
                maker.Ident(typeNode.toName("this")),
                List.<JCTree.JCTypeParameter>nil(),
                List.<JCTree.JCVariableDecl>nil(),
                List.<JCTree.JCExpression>nil(),
                body,
                null
        );
        createRelevantNonNullAnnotation(typeNode, methodDef);
        return recursiveSetGeneratedBy(methodDef, jcTree, typeNode.getContext());
    }

    private JCTree.JCMethodDecl createToJsonMethod(JavacNode typeNode, JCTree jcTree) {
        lombok.javac.JavacTreeMaker maker = typeNode.getTreeMaker();
        JCTree.JCModifiers mods = maker.Modifiers(Flags.PUBLIC);
        JCTree.JCExpression jcExpression = chainDotsString(typeNode, "com.alibaba.fastjson.JSON.toJSONString");
        JCTree.JCMethodInvocation memberAccessor = maker.Apply(
                List.<JCTree.JCExpression>nil(),
                jcExpression,
                List.<JCTree.JCExpression>of(maker.Ident(typeNode.toName("this")))
        );
        JCTree.JCStatement statements = maker.Return(memberAccessor);
        JCTree.JCBlock body = maker.Block(0, List.of(statements));

        JCTree.JCMethodDecl methodDef = maker.MethodDef(
                mods,
                typeNode.toName(TO_JSON_FIELD_NAME),
                genJavaLangTypeRef(typeNode, "String"),
                List.<JCTree.JCTypeParameter>nil(),
                List.<JCTree.JCVariableDecl>nil(),
                List.<JCTree.JCExpression>nil(),
                body,
                null
        );
        createRelevantNonNullAnnotation(typeNode, methodDef);
        return recursiveSetGeneratedBy(methodDef, jcTree, typeNode.getContext());
    }

    /**
     * todo
     *
     * public String toJson();
     * public static POJO fromJson(String)
     */
}
