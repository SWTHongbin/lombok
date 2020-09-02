package lombok.javac.handlers;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import lombok.Convertable;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import org.mangosdk.spi.ProviderFor;

import static lombok.javac.handlers.JavacHandlerUtil.*;

/**
 * @author lihongbin
 * Handles the {@code lombok.Convertable} annotation for javac.
 * <p>
 * public Class<?> toBean();
 * public static Object fromBean(Class<?> cls )
 */

@ProviderFor(JavacAnnotationHandler.class)
public class HandleConvert extends JavacAnnotationHandler<Convertable> {
    private static final String TO_BEAN_FIELD_NAME = "toBean",
            FROM_BEAN_FIELD_NAME = "fromBean",
            CLAZZ_PARAM_NAME = "clazz",
            PARAM_PARAM_NAME = "param";


    @Override
    public void handle(AnnotationValues<Convertable> annotation, JCAnnotation ast, JavacNode annotationNode) {
        deleteAnnotationIfNeccessary(annotationNode, Convertable.class);
        JavacNode typeNode = annotationNode.up();
        if (!isClass(typeNode)) {
            annotationNode.addError("@Convertable is only supported on a class.");
            return;
        }

        boolean notAClass = true;
        if (typeNode.get() instanceof JCTree.JCClassDecl) {
            long flags = ((JCTree.JCClassDecl) typeNode.get()).mods.flags;
            notAClass = (flags & (Flags.INTERFACE | Flags.ANNOTATION)) != 0;
        }

        if (notAClass) {
            annotationNode.addError("@Convertable is only supported on a class.");
            return;
        }

        if (methodExists(TO_BEAN_FIELD_NAME, typeNode, -1) != MemberExistsResult.NOT_EXISTS) {
            annotationNode.addWarning("Field '" + TO_BEAN_FIELD_NAME + "' already exists.");
        } else {
            JCTree.JCMethodDecl method = createToBeanMethod(typeNode, annotationNode.get());
            injectMethod(typeNode, method);
        }
        if (methodExists(FROM_BEAN_FIELD_NAME, typeNode, 1) != MemberExistsResult.NOT_EXISTS) {
            annotationNode.addWarning("Field '" + FROM_BEAN_FIELD_NAME + "' already exists.");
        } else {
            JCTree.JCMethodDecl method = createFromBeanMethod(typeNode, annotationNode.get());
            injectMethod(typeNode, method);
        }

    }

    private JCTree.JCMethodDecl createToBeanMethod(JavacNode typeNode, JCTree jcTree) {
        lombok.javac.JavacTreeMaker maker = typeNode.getTreeMaker();
        JCTree.JCModifiers mods = maker.Modifiers(Flags.PUBLIC);
        JCTree.JCStatement statements = getToBeanJcStatement(typeNode, maker);
        JCTree.JCVariableDecl jcVariableDecl = buildToBeanParam(typeNode, maker);
        JCTree.JCTypeParameter typaram = maker.TypeParameter(typeNode.toName("T"), List.<JCTree.JCExpression>nil());

        JCTree.JCMethodDecl methodDef = maker.MethodDef(
                mods,
                typeNode.toName(TO_BEAN_FIELD_NAME),
                maker.Ident(typeNode.toName("T")),
                List.<JCTree.JCTypeParameter>of(typaram),
                List.<JCTree.JCVariableDecl>of(jcVariableDecl),
                List.<JCTree.JCExpression>nil(),
                maker.Block(0, List.of(statements)),
                null
        );
        return recursiveSetGeneratedBy(methodDef, jcTree, typeNode.getContext());
    }

    private JCTree.JCMethodDecl createFromBeanMethod(JavacNode typeNode, JCTree jcTree) {
        lombok.javac.JavacTreeMaker maker = typeNode.getTreeMaker();
        JCTree.JCModifiers mods = maker.Modifiers(Flags.PUBLIC | Flags.STATIC);
        JCTree.JCClassDecl type = (JCTree.JCClassDecl) typeNode.get();
        JCTree.JCExpression returnType = maker.Ident(type.name);
        JCTree.JCStatement statements = getFromBeanStatement(typeNode, maker, returnType);
        JCTree.JCTypeParameter typaram = maker.TypeParameter(typeNode.toName("T"), List.<JCTree.JCExpression>nil());

        JCTree.JCMethodDecl methodDef = maker.MethodDef(
                mods,
                typeNode.toName(FROM_BEAN_FIELD_NAME),
                returnType,
                List.<JCTree.JCTypeParameter>of(typaram),
                List.<JCTree.JCVariableDecl>of(buildFromBeanParam(typeNode, maker)),
                List.<JCTree.JCExpression>nil(),
                maker.Block(0, List.of(statements)),
                null);

        createRelevantNonNullAnnotation(typeNode, methodDef);
        return recursiveSetGeneratedBy(methodDef, jcTree, typeNode.getContext());
    }

    private JCTree.JCStatement getToBeanJcStatement(JavacNode typeNode, lombok.javac.JavacTreeMaker maker) {
        ListBuffer<JCTree.JCExpression> args = new ListBuffer<JCTree.JCExpression>();
        args.append(maker.Ident(typeNode.toName("this")))
                .append(maker.Ident(typeNode.toName(CLAZZ_PARAM_NAME)));

        JCTree.JCMethodInvocation memberAccessor = maker.Apply(
                List.<JCTree.JCExpression>nil(),
                chainDots(typeNode, "com", "xyz", "utils", "JsonUtils", "convert"),
                args.toList()
        );
        return maker.Return(memberAccessor);
    }

    private JCTree.JCVariableDecl buildToBeanParam(JavacNode typeNode, lombok.javac.JavacTreeMaker maker) {
        long flags = addFinalIfNeeded(Flags.PARAMETER, typeNode.getContext());
        JCTree.JCVariableDecl aClass = maker.VarDef(maker.Modifiers(flags),
                typeNode.toName(CLAZZ_PARAM_NAME),
                maker.TypeApply(maker.Ident(typeNode.toName("Class")), List.<JCTree.JCExpression>of(maker.Ident(typeNode.toName("T")))),
                null);
        return aClass;
    }


    private JCTree.JCVariableDecl buildFromBeanParam(JavacNode typeNode, lombok.javac.JavacTreeMaker maker) {
        long flags = addFinalIfNeeded(Flags.PARAMETER, typeNode.getContext());
        JCTree.JCVariableDecl aClass = maker.VarDef(maker.Modifiers(flags),
                typeNode.toName(PARAM_PARAM_NAME),
                maker.Ident(typeNode.toName("T")),
                null);
        return aClass;
    }


    private JCTree.JCStatement getFromBeanStatement(JavacNode typeNode, lombok.javac.JavacTreeMaker maker, JCTree.JCExpression returnType) {
        ListBuffer<JCTree.JCExpression> args = new ListBuffer<JCTree.JCExpression>();
        args.append(maker.Ident(typeNode.toName(PARAM_PARAM_NAME)))
                .append(maker.Select(returnType, typeNode.toName("class")));

        JCTree.JCExpression jcExpression = chainDots(typeNode, "com", "xyz", "utils", "JsonUtils", "convert");
        JCTree.JCMethodInvocation memberAccessor = maker.Apply(List.<JCTree.JCExpression>nil(), jcExpression, args.toList());
        return maker.Return(memberAccessor);
    }

}
