package lombok.javac.handlers;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCExpression;
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
public class HandleConvertable extends JavacAnnotationHandler<Convertable> {
    private static final String TO_BEAN_FIELD_NAME = "toBean",
            FROM_BEAN_FIELD_NAME = "fromBean",
            POJO_PARAM_NAME = "pojo",
            BEAN_ANNOTATION_NAME = "bean";


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
            JCTree.JCMethodDecl method = createToBeanMethod(annotation, typeNode, annotationNode);
            injectMethod(typeNode, method);
        }
        if (methodExists(FROM_BEAN_FIELD_NAME, typeNode, 1) != MemberExistsResult.NOT_EXISTS) {
            annotationNode.addWarning("Field '" + FROM_BEAN_FIELD_NAME + "' already exists.");
        } else {
            JCTree.JCMethodDecl method = createFromBeanMethod(annotation, typeNode, annotationNode);
            injectMethod(typeNode, method);
        }

    }

    private JCTree.JCMethodDecl createToBeanMethod(AnnotationValues<Convertable> annotation, JavacNode typeNode, JavacNode annotationNode) {
        lombok.javac.JavacTreeMaker maker = typeNode.getTreeMaker();
        JCTree.JCModifiers mods = maker.Modifiers(Flags.PUBLIC);
        String beanClassName = getConvertBeanClassName(annotation);
        JCTree.JCExpression retrunType = maker.Ident(typeNode.toName(beanClassName));
        JCTree.JCStatement block = getToBeanJcStatement(retrunType, typeNode, maker);
        JCTree.JCMethodDecl methodDef = maker.MethodDef(
                mods,
                typeNode.toName(TO_BEAN_FIELD_NAME),
                retrunType,
                List.<JCTree.JCTypeParameter>nil(),
                List.<JCTree.JCVariableDecl>nil(),
                List.<JCTree.JCExpression>nil(),
                maker.Block(0, List.of(block)),
                null
        );
        return recursiveSetGeneratedBy(methodDef, annotationNode.get(), typeNode.getContext());
    }
    
    private String getConvertBeanClassName(AnnotationValues<Convertable> annotation) {
    	String beanName = annotation.getRawExpression(BEAN_ANNOTATION_NAME);
    	return beanName.lastIndexOf(".class")>0?beanName.substring(0, beanName.length()-6):beanName;
    }
    
    private JCTree.JCStatement getToBeanJcStatement(JCTree.JCExpression beanType, JavacNode typeNode, lombok.javac.JavacTreeMaker maker) {
        ListBuffer<JCTree.JCExpression> args = new ListBuffer<JCTree.JCExpression>();
        args.append(maker.Ident(typeNode.toName("this")))
                .append(maker.Select(beanType, typeNode.toName("class")));

        JCTree.JCMethodInvocation memberAccessor = maker.Apply(
                List.<JCTree.JCExpression>nil(),
                chainDots(typeNode, "com", "xyz", "utils", "JsonUtils", "convert"),
                args.toList()
        );
        return maker.Return(memberAccessor);
    }
    
    private JCTree.JCMethodDecl createFromBeanMethod(AnnotationValues<Convertable> annotation, JavacNode typeNode, JavacNode annotationNode) {
    	lombok.javac.JavacTreeMaker maker = typeNode.getTreeMaker();
        JCTree.JCModifiers mods = maker.Modifiers(Flags.PUBLIC | Flags.STATIC);
        String beanClassName = getConvertBeanClassName(annotation);
        JCTree.JCExpression paramType = maker.Ident(typeNode.toName(beanClassName));
        JCTree.JCVariableDecl parameter = buildFromBeanParam(paramType, typeNode, maker);
        
        JCTree.JCClassDecl type = (JCTree.JCClassDecl) typeNode.get();
        JCTree.JCExpression returnType = maker.Ident(type.name);
        JCTree.JCStatement block = getFromBeanStatement(returnType, typeNode, maker);

        JCTree.JCMethodDecl methodDef = maker.MethodDef(
                mods,
                typeNode.toName(FROM_BEAN_FIELD_NAME),
                returnType,
                List.<JCTree.JCTypeParameter>nil(),
                List.<JCTree.JCVariableDecl>of(parameter),
                List.<JCTree.JCExpression>nil(),
                maker.Block(0, List.of(block)),
                null);
 //       annotationNode.addError(String.format("methodDef: %s", methodDef));
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


    private JCTree.JCStatement getFromBeanStatement(JCTree.JCExpression returnType, JavacNode typeNode, lombok.javac.JavacTreeMaker maker) {
        ListBuffer<JCTree.JCExpression> args = new ListBuffer<JCTree.JCExpression>();
        args.append(maker.Ident(typeNode.toName(POJO_PARAM_NAME)))
                .append(maker.Select(returnType, typeNode.toName("class")));

        JCTree.JCExpression jcExpression = chainDots(typeNode, "com", "xyz", "utils", "JsonUtils", "convert");
        JCTree.JCMethodInvocation memberAccessor = maker.Apply(List.<JCTree.JCExpression>nil(), jcExpression, args.toList());
        return maker.Return(memberAccessor);
    }

}
