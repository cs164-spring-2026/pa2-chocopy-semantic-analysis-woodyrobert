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
        sym.put("int",new SpecialClassDefType(objectDef));
        sym.put("bool",new SpecialClassDefType(objectDef));
        sym.put("str",new SpecialClassDefType(objectDef));
        sym.put("None",new SpecialClassDefType(null));

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
        Type type=ValueType.annotationToValueType(varDef.var.type);

        return type;
    }
    
    @Override
    public Type analyze(FuncDef funcDef) {

        // Get params and return type to create a FuncType
        List<ValueType> params=new ArrayList<>();
        for(TypedVar param: funcDef.params){
            params.add(ValueType.annotationToValueType(param.type));
        }
        ValueType returnType=ValueType.annotationToValueType(funcDef.returnType);

        // Create FuncType
        FuncType funcType=new FuncType(params,returnType);

        // Return function type
        return funcType;
    }

    @Override
    public Type analyze(ClassDef classDef) {
        Identifier classId=classDef.getIdentifier();
        Identifier superClassId=classDef.superClass;
        String superClassName=superClassId.name;
        Type superClassType=globals.get(superClassName);
        ClassDefType classType=new ClassDefType(null);
        //Check if super class exists
        if(!globals.declares(superClassName)){
            errors.semError(superClassId,
                                "Super-class not defined: %s",
                                superClassName);
        }
        else if(!(superClassType instanceof ClassDefType)){
            errors.semError(superClassId,
                                "Super-class must be a class: %s",
                                superClassName);
        }
        else if((superClassType instanceof SpecialClassDefType)){
            errors.semError(superClassId,
                                "Cannot extend special class: %s",
                                superClassName);
        }
        else{
            classType=new ClassDefType((ClassDefType)superClassType,new SymbolTable<Type>(sym));
        }
        

        // Return function type
        return classType;
    }


}
