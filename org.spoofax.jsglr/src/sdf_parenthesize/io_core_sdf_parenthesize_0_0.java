package sdf_parenthesize;

import org.strategoxt.stratego_lib.*;
import org.strategoxt.lang.*;
import org.spoofax.interpreter.terms.*;
import static org.strategoxt.lang.Term.*;
import org.spoofax.interpreter.library.AbstractPrimitive;
import java.util.ArrayList;
import java.lang.ref.WeakReference;

@SuppressWarnings("all") public class io_core_sdf_parenthesize_0_0 extends Strategy 
{ 
  public static io_core_sdf_parenthesize_0_0 instance = new io_core_sdf_parenthesize_0_0();

  @Override public IStrategoTerm invoke(Context context, IStrategoTerm term)
  { 
    context.push("io_core_sdf_parenthesize_0_0");
    Fail0:
    { 
      term = io_wrap_1_0.instance.invoke(context, term, parenthesize_$Sdf2_0_0.instance);
      if(term == null)
        break Fail0;
      context.popOnSuccess();
      if(true)
        return term;
    }
    context.popOnFailure();
    return null;
  }
}