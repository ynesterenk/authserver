package shared.core.validation;

import java.util.function.BiConsumer;

public interface Rule<T> extends BiConsumer<T, ErrorState> {

}
