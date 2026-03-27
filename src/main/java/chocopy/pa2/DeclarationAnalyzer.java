package chocopy.pa2;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.SymbolTable;
import chocopy.common.astnodes.*;
import chocopy.common.analysis.types.*;
import chocopy.pa2.types.*;
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
        ClassDefType objectDef=new ClassDefType(null);
        sym.put("object",objectDef);
        sym.put("int",new ClassDefType(objectDef));
        sym.put("bool",new ClassDefType(objectDef));
        sym.put("str",new ClassDefType(objectDef));
        sym.put("None",new ClassDefType(null));

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
        checkAndAddDeclarations(program.declarations);
        
        return null;
    }

    @Override
    public Type analyze(VarDef varDef) {
        Type type=ValueType.annotationToValueType(varDef.var.type);

        //Check if name is a class
        checkShadowDeclaration(varDef);

        return type;
    }
    
    @Override
    public Type analyze(FuncDef funcDef) {
        // Check if name is a class
        checkShadowDeclaration(funcDef);

        // Get params and return type to create a FuncType
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
        checkAndAddParams(funcDef.params);

        // Check function declarations for errors
        checkAndAddDeclarations(funcDef.declarations);
        
        // Go back to original scope
        sym=oldSym;
        // Return function type
        return funcType;
    }

    @Override
    public Type analyze(ClassDef classDef) {
        Identifier classId=classDef.getIdentifier();
        String superClassName=classDef.superClass.name;
        Type superClassType=globals.get(superClassName);
        ClassDefType classType=new ClassDefType(null);
        //Check if super class exists
        if(!globals.declares(superClassName)){
            errors.semError(classId,
                                "Super-class not defined: %s",
                                superClassName);
        }
        if(!(superClassType instanceof ClassDefType)){
            errors.semError(classId,
                                "Super-class must be a class: %s",
                                superClassName);
        }
        else{
            classType=new ClassDefType((ClassDefType)superClassType);
        }

        

        // Create the function scope
        SymbolTable<Type> classScope = new SymbolTable<>(sym);
        SymbolTable<Type> oldSym=sym;
        sym=classScope;

        // Check function declarations for errors
        checkAndAddDeclarations(classDef.declarations);
        
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

        // The name is a class or function in global, then error
        if((globals.get(name) instanceof ClassDefType) || (globals.get(name) instanceof FuncType)){
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
        if(type==null || (type instanceof ClassDefType) || (type instanceof FuncType)){
            errors.semError(id,
                                "Not a nonlocal variable: %s",
                                name);
            return null;
        }
        sym.put(name, type);
        return null;
    }

    public void checkAndAddDeclarations(List<Declaration> declarations){
        for (Declaration decl : declarations) {
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
    }

    public void checkShadowDeclaration(Declaration decl){
        Identifier id=decl.getIdentifier();
        String name=id.name;
        if(globals.declares(name) && (globals.get(name) instanceof ClassDefType)){
            errors.semError(id,
                                "Cannot shadow class name: %s",
                                name);
        }
    }

    public void checkAndAddParams(List<TypedVar> params){
        for(TypedVar param: params){
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
            } 
            else if(globals.declares(name) && (globals.get(name) instanceof ClassDefType)){
                errors.semError(id,
                                    "Cannot shadow class name: %s",
                                    name);
            }
            else {
                sym.put(name, type);
            }
        }
    }

}
