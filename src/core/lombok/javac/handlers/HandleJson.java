package lombok.javac.handlers;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import lombok.JsonSerializable;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import org.mangosdk.spi.ProviderFor;

import static lombok.javac.handlers.JavacHandlerUtil.*;

/**
 * @author lihongbin
 * Handles the {@code lombok.JsonSerializable} annotation for javac.
 * <p>
 * public String toJson();
 * public static POJO fromJson(String)
 */

@ProviderFor(JavacAnnotationHandler.class)
public class HandleJson extends JavacAnnotationHandler<JsonSerializable> {
    private static final String TO_JSON_FIELD_NAME = "toJson",
            FROM_JSON_FIELD_NAME = "fromJson",
            JSON_STRING_PARAMETERS_NAME = "jsonStr";

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

    private JCTree.JCMethodDecl createToJsonMethod(JavacNode typeNode, JCTree jcTree) {
        lombok.javac.JavacTreeMaker maker = typeNode.getTreeMaker();

        JCTree.JCModifiers mods = maker.Modifiers(Flags.PUBLIC);
        JCTree.JCStatement statements = getToJsonJcStatement(typeNode, maker);
        JCTree.JCMethodDecl methodDef = maker.MethodDef(
                mods,
                typeNode.toName(TO_JSON_FIELD_NAME),
                genJavaLangTypeRef(typeNode, "String"),
                List.<JCTree.JCTypeParameter>nil(),
                List.<JCTree.JCVariableDecl>nil(),
                List.<JCTree.JCExpression>nil(),
                maker.Block(0, List.of(statements)),
                null
        );
        return recursiveSetGeneratedBy(methodDef, jcTree, typeNode.getContext());
    }

    private JCTree.JCMethodDecl createFromJsonMethod(JavacNode typeNode, JCTree jcTree) {
        lombok.javac.JavacTreeMaker maker = typeNode.getTreeMaker();
        JCTree.JCModifiers mods = maker.Modifiers(Flags.PUBLIC | Flags.STATIC);
        JCTree.JCClassDecl type = (JCTree.JCClassDecl) typeNode.get();
        JCTree.JCExpression returnType = namePlusTypeParamsToTypeReference(maker, typeNode, type.typarams);
        JCTree.JCStatement statements = getFromJsonStatement(typeNode, maker, returnType);

        JCTree.JCMethodDecl methodDef = maker.MethodDef(
                mods,
                typeNode.toName(FROM_JSON_FIELD_NAME),
                returnType,
                List.<JCTree.JCTypeParameter>nil(),
                List.<JCTree.JCVariableDecl>of(buildFromJsonParam(typeNode, maker)),
                List.<JCTree.JCExpression>nil(),
                maker.Block(0, List.of(statements)),
                null);

        createRelevantNonNullAnnotation(typeNode, methodDef);
        return recursiveSetGeneratedBy(methodDef, jcTree, typeNode.getContext());
    }

    private JCTree.JCStatement getFromJsonStatement(JavacNode typeNode, lombok.javac.JavacTreeMaker maker, JCTree.JCExpression returnType) {
        ListBuffer<JCTree.JCExpression> args = new ListBuffer<JCTree.JCExpression>();
        args.append(maker.Ident(typeNode.toName(JSON_STRING_PARAMETERS_NAME)))
                .append(maker.Select(returnType, typeNode.toName("class")));

        JCTree.JCExpression jcExpression = chainDots(typeNode, "com", "xyz", "utils", "JsonUtils", "jsonToBean");
        JCTree.JCMethodInvocation memberAccessor = maker.Apply(List.<JCTree.JCExpression>nil(), jcExpression, args.toList());
        return maker.Return(memberAccessor);
    }

    private JCTree.JCVariableDecl buildFromJsonParam(JavacNode typeNode, lombok.javac.JavacTreeMaker maker) {
        long flags = addFinalIfNeeded(Flags.PARAMETER, typeNode.getContext());
        return maker.VarDef(maker.Modifiers(flags), typeNode.toName(JSON_STRING_PARAMETERS_NAME)
                , genJavaLangTypeRef(typeNode, "String"),
                null);
    }


    private JCTree.JCStatement getToJsonJcStatement(JavacNode typeNode, lombok.javac.JavacTreeMaker maker) {
        JCTree.JCExpression jcExpression = chainDots(typeNode, "com", "xyz", "utils", "JsonUtils", "beanToJson");
        JCTree.JCMethodInvocation memberAccessor = maker.Apply(
                List.<JCTree.JCExpression>nil(),
                jcExpression,
                List.<JCTree.JCExpression>of(maker.Ident(typeNode.toName("this")))
        );
        return maker.Return(memberAccessor);
    }


}
