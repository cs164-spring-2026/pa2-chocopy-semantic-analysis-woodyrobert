package chocopy.pa2;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.Type;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.astnodes.Declaration;
import chocopy.common.astnodes.Errors;
import chocopy.common.astnodes.Identifier;
import chocopy.common.astnodes.Program;
import chocopy.common.astnodes.VarDef;
import chocopy.common.astnodes.*;
import chocopy.common.analysis.types.*;
import java.util.*;

/**
 * Analyzes declarations to create a top-level symbol table.
 */
public class DeclarationAnalyzer extends AbstractNodeAnalyzer<Type> {

    /** Current symbol table.  Changes with new declarative region. */
    private SymbolTable<Type> sym = new SymbolTable<>();
    /** Global symbol table. */
    private final SymbolTable<Type> globals = sym;
    /** Receiver for semantic error messages. */
    private final Errors errors;

    /** A new declaration analyzer sending errors to ERRORS0. */
    public DeclarationAnalyzer(Errors errors0) {
        errors = errors0;

        // Keywords that can't be variables
        sym.put("object",Type.OBJECT_TYPE);
        sym.put("int",Type.INT_TYPE);
        sym.put("bool",Type.BOOL_TYPE);
        sym.put("str",Type.STR_TYPE);
        sym.put("None",Type.NONE_TYPE);

        // Predefined functions
        List<ValueType> printParams=new ArrayList<>();
        printParams.add(Type.OBJECT_TYPE);
        sym.put("print", new FuncType(printParams,Type.NONE_TYPE));
        sym.put("input",new FuncType(new ArrayList<>(),Type.STR_TYPE));
        List<ValueType> lenParams=new ArrayList<>();
        lenParams.add(Type.OBJECT_TYPE);
        sym.put("len",new FuncType(lenParams,Type.INT_TYPE));
    }


    public SymbolTable<Type> getGlobals() {
        return globals;
    }

    @Override
    public Type analyze(Program program) {
        for (Declaration decl : program.declarations) {
            Identifier id = decl.getIdentifier();
            String name = id.name;

            Type type = decl.dispatch(this);

            if (type == null) {
                continue;
            }

            if (sym.declares(name)) {
                errors.semError(id,
                                "Duplicate declaration of identifier in same "
                                + "scope: %s",
                                name);
            } else {
                sym.put(name, type);
            }
        }

        return null;
    }

    @Override
    public Type analyze(VarDef varDef) {
        return ValueType.annotationToValueType(varDef.var.type);
    }
    
    @Override
    public Type analyze(FuncDef funcDef) {

        // Get params and return to create a FuncType
        List<ValueType> params=new ArrayList<>();
        for(TypedVar param: funcDef.params){
            params.add(ValueType.annotationToValueType(param.type));
        }
        ValueType returnType=ValueType.annotationToValueType(funcDef.returnType);

        // Create FuncType
        FuncType funcType=new FuncType(params,returnType);

        // Create the function scope
        SymbolTable<Type> funcScope = new SymbolTable<>(sym);
        SymbolTable<Type> oldSym=sym;
        sym=funcScope;

        // Add parameters to function scope
        for(TypedVar param: funcDef.params){
            Identifier id = param.identifier;
            String name = id.name;
            Type type=ValueType.annotationToValueType(param.type);

            if (type == null) {
                continue;
            }

            if (sym.declares(name)) {
                errors.semError(id,
                                "Duplicate declaration of identifier in same "
                                + "scope: %s",
                                name);
            } else {
                sym.put(name, type);
            }
        }

        // Check function declarations for errors
        for (Declaration decl : funcDef.declarations) {
            Identifier id = decl.getIdentifier();
            String name = id.name;

            Type type = decl.dispatch(this);

            if (type == null) {
                continue;
            }

            if (sym.declares(name)) {
                errors.semError(id,
                                "Duplicate declaration of identifier in same "
                                + "scope: %s",
                                name);
            } else {
                sym.put(name, type);
            }
        }
        // Go back to original scope
        sym=oldSym;
        // Return function type
        return funcType;
    }

    @Override
    public Type analyze(ClassDef classDef) {

        // Create ClassValueType
        ClassValueType classType=new ClassValueType(classDef.getIdentifier().name);

        // Create the function scope
        SymbolTable<Type> classScope = new SymbolTable<>(sym);
        SymbolTable<Type> oldSym=sym;
        sym=classScope;

        // Check function declarations for errors
        for (Declaration decl : classDef.declarations) {
            Identifier id = decl.getIdentifier();
            String name = id.name;

            Type type = decl.dispatch(this);

            if (type == null) {
                continue;
            }

            if (sym.declares(name)) {
                errors.semError(id,
                                "Duplicate declaration of identifier in same "
                                + "scope: %s",
                                name);
            } else {
                sym.put(name, type);
            }
        }
        // Go back to original scope
        sym=oldSym;
        // Return function type
        return classType;
    }

    @Override
    public Type analyze(GlobalDecl globalDecl) {
        Identifier id=globalDecl.getIdentifier();
        String name=id.name;
        // If the scope already have the name, error
        if(sym.declares(name)){
            errors.semError(id,
                                "Duplicate declaration of identifier in same "
                                + "scope: %s",
                                name);
            return null;
        }
        // If the global scope doesn't have the name, error
        if(!globals.declares(name) ){
            errors.semError(id,
                                "Not a global variable: %s",
                                name);
            return null;
        }

        // The name is not a variable in global
        if((globals.get(name) instanceof ClassValueType) || (globals.get(name) instanceof FuncType)){
            errors.semError(id,
                                "Not a global variable: %s",
                                name);
            return null;
        }
        // Put the name into the local scope
        sym.put(name, globals.get(name));
        
        return null;
    }

    @Override
    public Type analyze(NonLocalDecl nonlocalDecl) {
        Identifier id=nonlocalDecl.getIdentifier();
        String name=id.name;
        if(sym.declares(name)){
            errors.semError(id,
                                "Duplicate declaration of identifier in same "
                                + "scope: %s",
                                name);
            return null;
        }

        // Loop across the outer scopes, excluding the global
        Type type=null;
        SymbolTable<Type> parentTable=sym.getParent();

        while(parentTable!=null && parentTable.getParent()!=null){
            if(parentTable.declares(name)){
                type=parentTable.get(name);
                break;
            }
            parentTable=parentTable.getParent();
        }
        if(type==null || (type instanceof ClassValueType) || (type instanceof FuncType)){
            errors.semError(id,
                                "Not a nonlocal variable: %s",
                                name);
            return null;
        }
        sym.put(name, type);
        return null;
    }

}
