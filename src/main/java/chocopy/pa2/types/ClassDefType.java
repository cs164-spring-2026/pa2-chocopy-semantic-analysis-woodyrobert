package chocopy.pa2;

import java.util.ArrayList;
import java.util.List;
import chocopy.common.analysis.types.*;

import com.fasterxml.jackson.annotation.JsonCreator;

/** Semantic information for a class. */
public class ClassDefType extends Type {

    /** Class's super class type. */
    public final ClassDefType superType;

    /** Create a ClassType returning superType0. */
    public ClassDefType(ClassDefType superType0) {
        this.superType=superType0;
    }


    @Override
    public String toString() {
        return "<class>";
    }

}
