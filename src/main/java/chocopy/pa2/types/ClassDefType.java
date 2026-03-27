package chocopy.pa2;

import java.util.ArrayList;
import java.util.List;
import chocopy.common.analysis.types.*;
import chocopy.common.analysis.SymbolTable;

import com.fasterxml.jackson.annotation.JsonCreator;

/** Semantic information for a class. */
public class ClassDefType extends Type {

    /** Class's super class type. */
    public final ClassDefType superType;
    public final SymbolTable scope;

    /** Create a ClassType returning superType0. */
    public ClassDefType(ClassDefType superType0,SymbolTable<Type> scope) {
        this.superType=superType0;
        this.scope=scope;
    }
    public ClassDefType(ClassDefType superType0) {
        this(superType0,null);
    }


    @Override
    public String toString() {
        return "<class>";
    }

}
