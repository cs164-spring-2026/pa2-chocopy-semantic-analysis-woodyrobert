package chocopy.pa2;

import java.util.ArrayList;
import java.util.List;
import chocopy.common.analysis.types.*;

import com.fasterxml.jackson.annotation.JsonCreator;

/** Semantic information for a class. */
public class SpecialClassDefType extends ClassDefType {


    public SpecialClassDefType(ClassDefType superType0, String name){
        super(superType0, name);
    }


    @Override
    public String toString() {
        return "<class>";
    }

}
