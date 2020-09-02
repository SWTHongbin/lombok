package lombok;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author lihongbin
 * <p>
 * convert pojo to Class
 * <p>
 * public class ConvertExample {
 * <p>
 * public <T> T toBean(Class<T> cls) throws IllegalAccessException, InstantiationException {
 * return cls.newInstance();
 * }
 * <p>
 * public static <T> ConvertExample fromBean(T t) {
 * ConvertExample convertExample = new ConvertExample();
 * return convertExample;
 * }
 * <p>
 * //get/set methds here
 * .........
 * }
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Convertable {
}
