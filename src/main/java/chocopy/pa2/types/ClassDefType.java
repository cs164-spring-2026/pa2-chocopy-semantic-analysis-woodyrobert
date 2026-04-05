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
    public final String name;

    /** Create a ClassType returning superType0. */
    public ClassDefType(ClassDefType superType0,SymbolTable<Type> scope, String name) {
        this.superType=superType0;
        this.scope=scope;
        this.name = name;
    }
    public ClassDefType(ClassDefType superType0, String name) {
        this(superType0,null,name);
    }


    @Override
    public String toString() {
        return "<class>";
    }

}
