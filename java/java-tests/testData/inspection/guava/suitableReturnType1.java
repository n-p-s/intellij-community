import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Main20 {

  Iterable<String> get() {
    return FluentIterable.f<caret>rom(new ArrayList<String>()).transform(String::trim);
  }

}