package lombok.javac.handlers;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import lombok.Validation;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import org.mangosdk.spi.ProviderFor;

import static lombok.javac.handlers.JavacHandlerUtil.*;

/**
 * @author lihongbin
 */
@ProviderFor(JavacAnnotationHandler.class)
public class HandleValidation extends JavacAnnotationHandler<Validation> {

    @Override
    public void handle(AnnotationValues<Validation> annotation, JCAnnotation ast, JavacNode annotationNode) {
        deleteAnnotationIfNeccessary(annotationNode, Validation.class);
        JavacNode typeNode = annotationNode.up();
        if (!isClass(typeNode)) {
            annotationNode.addError("@Validation is only supported on a class.");
            return;
        }
        boolean notAClass = true;
        if (typeNode.get() instanceof JCTree.JCClassDecl) {
            long flags = ((JCTree.JCClassDecl) typeNode.get()).mods.flags;
            notAClass = (flags & (Flags.INTERFACE | Flags.ANNOTATION)) != 0;
        }

        if (notAClass) {
            annotationNode.addError("@Validation is only supported on a class.");
            return;
        }
        String className = typeNode.getName();
        if (typeNode.get() instanceof JCTree.JCClassDecl) {
            for (JCTree def : ((JCTree.JCClassDecl) typeNode.get()).defs) {
                if (!(def instanceof JCTree.JCMethodDecl)) {
                    continue;
                }
                JCTree.JCMethodDecl md = (JCTree.JCMethodDecl) def;
                String name = md.name.toString();
                if (name.contentEquals("<init>") || name.equals(className)) {
                    continue;
                }
                for (JCTree.JCVariableDecl x : md.getParameters()) {
                    addAnnotation(x.mods, typeNode, x.pos, x, typeNode.getContext(), "org.springframework.validation.annotation.Validated", null);
                }
            }
        }
    }

}
