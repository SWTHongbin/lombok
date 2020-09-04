package lombok;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>
 * Convert pojo to another bean:
 * @Convertable(bean = AnotherPojo.class)
 * public class Pojo {
 * ......
 * }
 * <p>
 * public class AnotherPojo {
 * ......
 * }
 * <p>
 * will generate codes:
 * <p>
 * public class Pojo {
 * <p>
 * public AnotherPojo toBean() {
 * return JsonUtils.convert(this, AnotherPojo.class.getName());
 * }
 * <p>
 * public static Pojo fromBean(AnotherPojo apojo) {
 * return JsonUtils.convert(apojo, Pojo.class);
 * }
 * <p>
 * }
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Convertable {
    Class<?> bean();
}
