package vtk.text.tl.expr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import vtk.text.tl.Context;
import vtk.text.tl.Symbol;

public class Append extends Function {

    public Append(Symbol symbol) {
        super(symbol, 2);
    }
    
    @Override
    public Object eval(Context ctx, Object... args) {
        Object arg0 = args[0];
        Object arg1 = args[1];
        
        if (!(arg0 instanceof List<?>)) 
            throw new IllegalArgumentException(
                    "append: first argument is not a list: " + arg0);

        List<Object> newList = new ArrayList<>((List<?>) arg0);
        newList.add(arg1);
        return Collections.unmodifiableList(newList);
    }

}
