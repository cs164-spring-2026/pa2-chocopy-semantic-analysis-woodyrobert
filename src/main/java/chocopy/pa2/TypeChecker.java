package chocopy.pa2;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.SymbolTable;


import static chocopy.common.analysis.types.Type.INT_TYPE;
import static chocopy.common.analysis.types.Type.OBJECT_TYPE;
import chocopy.common.analysis.types.*;
import chocopy.common.astnodes.*;
import chocopy.pa2.types.*;
import java.util.*;


/** Analyzer that performs ChocoPy type checks on all nodes.  Applied after
 *  collecting declarations. */
public class TypeChecker extends AbstractNodeAnalyzer<Type> {

    /** The current symbol table (changes depending on the function
     *  being analyzed). */
    private SymbolTable<Type> sym;
    private final SymbolTable<Type> globals;
    private String inClassName;
    /** Collector for errors. */
    private Errors errors;


    /** Creates a type checker using GLOBALSYMBOLS for the initial global
     *  symbol table and ERRORS0 to receive semantic errors. */
    public TypeChecker(SymbolTable<Type> globalSymbols, Errors errors0) {
        inClassName=null;
        globals=globalSymbols;
        sym = globalSymbols;
        errors = errors0;
    }

    /** Inserts an error message in NODE if there isn't one already.
     *  The message is constructed with MESSAGE and ARGS as for
     *  String.format. */
    private void err(Node node, String message, Object... args) {
        errors.semError(node, message, args);
    }

    @Override
    public Type analyze(Program program) {
        for (Declaration decl : program.declarations) {
            decl.dispatch(this);
        }
        for (Stmt stmt : program.statements) {
            stmt.dispatch(this);
        }
        return null;
    }

    @Override
    public Type analyze(FuncDef funcDef) {

        

        // Create the function scope
        SymbolTable<Type> funcScope = new SymbolTable<>(sym);
        SymbolTable<Type> oldSym=sym;
        sym=funcScope;

        // Add to function scope and get params for FuncType
        List<ValueType> params=checkAndAddParams(funcDef.params);

        // Check function declarations for errors
        checkAndAddDeclarations(funcDef.declarations);
        
        // Go back to original scope
        sym=oldSym;
        // Get return type to create a FuncType
        ValueType returnType=(ValueType)funcDef.returnType.dispatch(this);

        FuncType funcType=new FuncType(params,returnType);
        // Return function type
        return funcType;
    }

    @Override
    public Type analyze(ClassDef classDef) {
        // Create the function scope
        ClassDefType classDefType=(ClassDefType)globals.get(classDef.getIdentifier().name);
        SymbolTable<Type> classScope = classDefType.scope;
        SymbolTable<Type> oldSym=sym;
        sym=classScope;
        inClassName=classDef.getIdentifier().name;
        // Check function declarations for errors
        checkAndAddDeclarations(classDef.declarations);
        inClassName=null;
        // Go back to original scope
        sym=oldSym;
        // Return function type
        return null;
    }

    @Override
    public Type analyze(ExprStmt s) {
        s.expr.dispatch(this);
        return null;
    }

    @Override
    public Type analyze(IntegerLiteral i) {
        return i.setInferredType(Type.INT_TYPE);
    }

    @Override
    public Type analyze(BinaryExpr e) {
        Type t1 = e.left.dispatch(this);
        Type t2 = e.right.dispatch(this);

        switch (e.operator) {
        case "-":
        case "*":
        case "//":
        case "%":
            if (INT_TYPE.equals(t1) && INT_TYPE.equals(t2)) {
                return e.setInferredType(INT_TYPE);
            } else {
                err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                    e.operator, t1, t2);
                return e.setInferredType(INT_TYPE);
            }
        default:
            return e.setInferredType(OBJECT_TYPE);
        }

    }

    @Override
    public Type analyze(Identifier id) {
        String varName = id.name;
        Type varType = sym.get(varName);

        if (varType != null && varType.isValueType()) {
            return id.setInferredType(varType);
        }

        err(id, "Not a variable: %s", varName);
        return id.setInferredType(ValueType.OBJECT_TYPE);
    }

    @Override
    public Type analyze(GlobalDecl globalDecl) {
        Identifier id=globalDecl.getIdentifier();
        String name=id.name;
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
        
        return globals.get(name);
    }
    @Override
    public Type analyze(VarDef varDef) {
        Type type=varDef.var.dispatch(this);

        return type;
    }

    @Override
    public Type analyze(NonLocalDecl nonlocalDecl) {
        Identifier id=nonlocalDecl.getIdentifier();
        String name=id.name;

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
        return type;
    }

    @Override
    public Type analyze(TypedVar typedVar) {
        Type valueType=typedVar.type.dispatch(this);
        
        return valueType;
    }

    @Override
    public Type analyze(ClassType classType) {
        Type valueType=ValueType.annotationToValueType(classType);
        String typeName=valueType.toString();
        if(!globals.declares(typeName)){
                errors.semError(classType,
                                    "Invalid type annotation; there is no class named: %s",
                                    typeName);
            }
        
        return valueType;
    }
    @Override
    public Type analyze(ListType listType) {
        Type valueType=ValueType.annotationToValueType(listType);
        Type baseType = valueType;
        while (baseType instanceof ListValueType) {
            baseType = ((ListValueType) baseType).elementType;
        }
        String typeName=baseType.toString();
        if(!globals.declares(typeName)){
                errors.semError(listType,
                                    "Invalid type annotation; there is no class named: %s",
                                    typeName);
            }
        
        return valueType;
    }


    // Utility Functions

    public void checkAndAddDeclarations(List<Declaration> declarations){
        
        boolean isClass=inClassName!=null;
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
                continue;
            } 
            if(globals.declares(name) && (globals.get(name) instanceof ClassDefType)){
                errors.semError(id,
                                    "Cannot shadow class name: %s",
                                    name);
                continue;
            }
            if(isClass){
                ClassDefType parentClassDefType=((ClassDefType)globals.get(inClassName)).superType;
                if(superClassScopeAttrCollide(name,parentClassDefType,type)){
                    errors.semError(id,
                                    "Cannot re-define attribute: %s",
                                    name);
                    continue;
                } 
                else if(decl instanceof FuncDef && methodParamCheck((FuncType)type)){
                    errors.semError(id,
                                "First parameter of the following method must be of the enclosing class: %s",
                                name);
                    continue;
                }
                else if(decl instanceof FuncDef && methodOverrideCheck(name,parentClassDefType,(FuncType)type)){
                    errors.semError(id,
                                    "Method overridden with different type signature: %s",
                                    name);
                    continue;
                }
            }
            
            sym.put(name, type);
            
        }
    }

    public boolean methodOverrideCheck(String name, ClassDefType parentClassDefType,FuncType funcType){
        SymbolTable<Type> scope=parentClassDefType.scope;
        if(scope!=null && scope.get(name)!=null){
            FuncType overriddenFuncType=(FuncType)scope.get(name); //This can be converted because all other types are founded in error check 
            if(funcType.parameters.size()!=overriddenFuncType.parameters.size()){
                return true;
            }
            for(int i=1; i<funcType.parameters.size();i++){
                if (!funcType.getParamType(i).toString().equals((overriddenFuncType.getParamType(i)).toString())) {
                    return true;
                }
            }
            if(!funcType.returnType.toString().equals(overriddenFuncType.returnType.toString())){
                return true;
            }
        }
        return false;
    }

    public boolean superClassScopeAttrCollide(String name, ClassDefType parentClassDefType, Type declType){
        
        SymbolTable<Type> scope=parentClassDefType.scope;
        if(scope!=null && scope.get(name)!=null && (scope.get(name) instanceof ValueType || declType instanceof ClassValueType)){
            return true;
        }
        return false;
    }

    public boolean methodParamCheck(FuncType funcType){
        List<ValueType> params=funcType.parameters;
        if (params.isEmpty() 
            || !(params.get(0) instanceof ClassValueType) 
            || !((ClassValueType)params.get(0)).toString().equals(inClassName)) {
                return true;
        }
        return false;
    }

    public List<ValueType> checkAndAddParams(List<TypedVar> params){
        List<ValueType> returnParams=new ArrayList<>();
        for(TypedVar param: params){
            Identifier id = param.identifier;
            String name = id.name;

            Type type = param.dispatch(this);

            if (type == null) {
                continue;
            }

            if (sym.declares(name)) {
                errors.semError(id,
                                "Duplicate declaration of identifier in same "
                                + "scope: %s",
                                name);
            } else if(globals.declares(name) && (globals.get(name) instanceof ClassDefType)){
                errors.semError(id,
                                    "Cannot shadow class name: %s",
                                    name);
            } else {
                sym.put(name, type);
                returnParams.add((ValueType)type);
            }
            
        }
        return returnParams;
    }
}
