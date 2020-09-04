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
import lombok.javac.JavacTreeMaker;

import org.mangosdk.spi.ProviderFor;

import static lombok.javac.handlers.JavacHandlerUtil.*;

/**
 * Handles the {@code lombok.Convertable} annotation for javac.
 * <p>
 * public AnotherPojo toBean();
 * public static Pojo fromBean(AnotherPojo bean)
 */

@ProviderFor(JavacAnnotationHandler.class)
public class HandleConvertable extends JavacAnnotationHandler<Convertable> {
    private static final String TO_BEAN_METHOD_NAME = "toBean",
            FROM_BEAN_METHOD_NAME = "fromBean",
            POJO_PARAM_NAME = "pojo",
            BEAN_ANNOTATION_NAME = "bean",
            CONVERT_METHOD = "com.xyz.utils.JsonUtils.convert";


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

        if (methodExists(TO_BEAN_METHOD_NAME, typeNode, -1) != MemberExistsResult.NOT_EXISTS) {
            annotationNode.addWarning("Method '" + TO_BEAN_METHOD_NAME + "' already exists.");
        } else {
            JCTree.JCMethodDecl method = createToBeanMethod(annotation, typeNode, annotationNode);
            injectMethod(typeNode, method);
        }
        if (methodExists(FROM_BEAN_METHOD_NAME, typeNode, 1) != MemberExistsResult.NOT_EXISTS) {
            annotationNode.addWarning("Method '" + FROM_BEAN_METHOD_NAME + "' already exists.");
        } else {
            JCTree.JCMethodDecl method = createFromBeanMethod(annotation, typeNode, annotationNode);
            injectMethod(typeNode, method);
        }

    }

    private JCTree.JCMethodDecl createToBeanMethod(AnnotationValues<Convertable> annotation, JavacNode typeNode, JavacNode annotationNode) {
        JavacTreeMaker maker = typeNode.getTreeMaker();
        JCTree.JCModifiers mods = maker.Modifiers(Flags.PUBLIC);
        JCTree.JCExpression retrunType = getAnnotatedClassType(annotation, maker, typeNode);
        JCTree.JCStatement block = buildToBeanJcStatement(retrunType, maker, typeNode);
        JCTree.JCMethodDecl methodDef = maker.MethodDef(
                mods,
                typeNode.toName(TO_BEAN_METHOD_NAME),
                retrunType,
                List.<JCTree.JCTypeParameter>nil(),
                List.<JCTree.JCVariableDecl>nil(),
                List.<JCTree.JCExpression>nil(),
                maker.Block(0, List.of(block)),
                null
        );
        return recursiveSetGeneratedBy(methodDef, annotationNode.get(), typeNode.getContext());
    }
    
    private JCTree.JCExpression getAnnotatedClassType(AnnotationValues<Convertable> annotation, JavacTreeMaker maker, JavacNode typeNode) {
    	String beanName = annotation.getRawExpression(BEAN_ANNOTATION_NAME);
    	beanName = beanName.lastIndexOf(".class")>0?beanName.substring(0, beanName.length()-6):beanName;
    	return maker.Ident(typeNode.toName(beanName));
    }
    
    private JCTree.JCStatement buildToBeanJcStatement(JCTree.JCExpression beanType, lombok.javac.JavacTreeMaker maker, JavacNode typeNode) {
        ListBuffer<JCTree.JCExpression> args = new ListBuffer<JCTree.JCExpression>();
        args.append(maker.Ident(typeNode.toName("this"))).append(maker.Select(beanType, typeNode.toName("class")));

        JCTree.JCMethodInvocation convertStatement = maker.Apply(
                List.<JCTree.JCExpression>nil(),
                chainDotsString(typeNode, CONVERT_METHOD),
                args.toList()
        );
        return maker.Return(convertStatement);
    }
    
    private JCTree.JCMethodDecl createFromBeanMethod(AnnotationValues<Convertable> annotation, JavacNode typeNode, JavacNode annotationNode) {
    	JavacTreeMaker maker = typeNode.getTreeMaker();
        JCTree.JCModifiers mods = maker.Modifiers(Flags.PUBLIC | Flags.STATIC);
        JCTree.JCExpression paramType = getAnnotatedClassType(annotation, maker, typeNode);
        JCTree.JCVariableDecl parameter = buildFromBeanParam(paramType, typeNode, maker);
        JCTree.JCClassDecl type = (JCTree.JCClassDecl) typeNode.get();
        JCTree.JCExpression returnType = maker.Ident(type.name);
        JCTree.JCStatement block = buildFromBeanJcStatement(returnType, typeNode, maker);

        JCTree.JCMethodDecl methodDef = maker.MethodDef(
                mods,
                typeNode.toName(FROM_BEAN_METHOD_NAME),
                returnType,
                List.<JCTree.JCTypeParameter>nil(),
                List.<JCTree.JCVariableDecl>of(parameter),
                List.<JCTree.JCExpression>nil(),
                maker.Block(0, List.of(block)),
                null);
        createRelevantNonNullAnnotation(typeNode, methodDef);
        return recursiveSetGeneratedBy(methodDef, annotationNode.get(), typeNode.getContext());
    }

    private JCTree.JCVariableDecl buildFromBeanParam(JCTree.JCExpression paramType, JavacNode typeNode, lombok.javac.JavacTreeMaker maker) {
        long flags = addFinalIfNeeded(Flags.PARAMETER, typeNode.getContext());
        return maker.VarDef(maker.Modifiers(flags),
                typeNode.toName(POJO_PARAM_NAME),
                paramType,
                null);
    }

    private JCTree.JCStatement buildFromBeanJcStatement(JCTree.JCExpression returnType, JavacNode typeNode, lombok.javac.JavacTreeMaker maker) {
        ListBuffer<JCTree.JCExpression> args = new ListBuffer<JCTree.JCExpression>();
        args.append(maker.Ident(typeNode.toName(POJO_PARAM_NAME)))
                .append(maker.Select(returnType, typeNode.toName("class")));

        JCTree.JCExpression jcExpression = chainDotsString(typeNode, CONVERT_METHOD);
        JCTree.JCMethodInvocation convertStatement = maker.Apply(List.<JCTree.JCExpression>nil(), jcExpression, args.toList());
        return maker.Return(convertStatement);
    }
}
