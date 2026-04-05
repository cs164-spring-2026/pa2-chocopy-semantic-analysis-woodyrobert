package chocopy.pa2;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.SymbolTable;

import static chocopy.common.analysis.types.Type.BOOL_TYPE;
import static chocopy.common.analysis.types.Type.EMPTY_TYPE;
import static chocopy.common.analysis.types.Type.INT_TYPE;
import static chocopy.common.analysis.types.Type.NONE_TYPE;
import static chocopy.common.analysis.types.Type.OBJECT_TYPE;
import static chocopy.common.analysis.types.Type.STR_TYPE;

import chocopy.common.analysis.types.*;
import chocopy.common.astnodes.*;
import chocopy.pa2.types.*;
import java_cup.runtime.Symbol;
import proguard.classfile.attribute.preverification.ObjectType;

import java.util.*;

import javax.swing.text.html.parser.Element;


/** Analyzer that performs ChocoPy type checks on all nodes.  Applied after
 *  collecting declarations. */
public class TypeChecker extends AbstractNodeAnalyzer<Type> {

    /** The current symbol table (changes depending on the function
     *  being analyzed). */
    private SymbolTable<Type> sym;
    private final SymbolTable<Type> globals;
    private String inClassName;
    private ValueType returnType;
    /** Collector for errors. */
    private Errors errors;


    /** Creates a type checker using GLOBALSYMBOLS for the initial global
     *  symbol table and ERRORS0 to receive semantic errors. */
    public TypeChecker(SymbolTable<Type> globalSymbols, Errors errors0) {
        inClassName=null;
        returnType=null;
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
        Identifier id=funcDef.getIdentifier();
        String name=id.name;

        // Create the function scope
        SymbolTable<Type> funcScope = new SymbolTable<>(sym);
        SymbolTable<Type> oldSym=sym;
        sym=funcScope;
        returnType=(ValueType)funcDef.returnType.dispatch(this);

        // Add to function scope and get params for FuncType
        List<ValueType> params=checkAndAddParams(funcDef.params);
        
        // Check function declarations for errors
        checkAndAddDeclarations(funcDef.declarations);
        
        for (Stmt stmt : funcDef.statements) {
            stmt.dispatch(this);
        }
        // Go back to original scope
        sym=oldSym;
        returnType=null;
        ValueType returnValType = (ValueType)funcDef.returnType.dispatch(this);
        if(inClassName!= null && name.equals("__init__")){
            // if(!returnType.className().equals("<None>")){
            //     errors.semError(id,
            //         "Expected type `<None>`; got type `%s`", returnType);
            // }
            if(!(params.size() == 1 && funcDef.params.get(0).identifier.name.equals("self"))){
                errors.semError(id,
                    "Method overridden with different type signature: __init__");
            }
        }
        if(!containsReturn(funcDef.statements) && !returnValType.className().equals("<None>")){
            errors.semError(id,
                                "All paths in this function/method must have a return statement: %s",
                                name);
        }
        FuncType funcType=new FuncType(params,returnValType);
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
        if(!classScope.declares("__init__")){
            ArrayList<ValueType> parameters = new ArrayList<ValueType>();
            parameters.add(new ClassValueType(classDef.getIdentifier().name));
            classScope.put("__init__", new FuncType(parameters, NONE_TYPE));
        }
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
    public Type analyze(AssignStmt s) {
        // if(s.value == null)
        //     return null;
        Type outputValTypeProbably = (s.value).dispatch(this);
        Boolean checked = false;
        for(Expr e: s.targets){
            Type baseType=e.dispatch(this);
            if (e instanceof Identifier) {
                String name = ((Identifier) e).name;
                if (!sym.declares(name) && sym.get(name) != null) {
                    errors.semError(e,
                                "Cannot assign to variable that is not explicitly declared in this scope: %s",
                                name);
                    
                }
                if(!baseType.equals(outputValTypeProbably) && !checked){
                    errors.semError(s,
                        "Expected type `%s`; got type `%s`",
                        baseType, outputValTypeProbably);
                    checked = true;
                }
            }
        }
        return null;
    }
    @Override
    public Type analyze(ListExpr L){
        if(L.elements.isEmpty()){
            return L.setInferredType(EMPTY_TYPE);
        }

        return LUB(L.elements);
    }

    public Type LUB(List<Expr> elements){
        Type currType = null;
        for(Expr e : elements){
            Type tempType = e.dispatch(this);
            if(tempType instanceof ListValueType){
                return OBJECT_TYPE;
            }
            if(currType == null){
                currType = tempType;
                continue;
            }
            if(tempType.className().equals(currType.className())){
                continue;
            }else{
                ClassDefType baseType = (ClassDefType)globals.get(tempType.className());
                ClassDefType baseCurrType = (ClassDefType)globals.get(currType.className());
                while(!baseType.name.equals(baseCurrType.name)){
                    if(baseType.superType == null){
                        baseCurrType = baseCurrType.superType;
                        baseType = (ClassDefType)globals.get(tempType.className());
                    }else{
                        baseType = baseType.superType;
                    }
                }
                currType = new ClassValueType(baseCurrType.name);
            }
        }
        return currType;
    }
    @Override
    public Type analyze(CallExpr c){
        FuncType f = null;
        if(sym.get(c.function.name) instanceof FuncType){
            f = (FuncType)sym.get(c.function.name);
        }else if (sym.get(c.function.name) instanceof ClassDefType){
            ClassDefType tempCDType = (ClassDefType)sym.get(c.function.name);
            SymbolTable<Type> tempScope = tempCDType.scope;
            f = (FuncType)tempScope.get("__init__");
        }
        else{
            return null;
        }
        if(c.args.size() == f.parameters.size()){
            for(int i = 0; i < c.args.size(); i++){
                Type cType = c.args.get(i).dispatch(this);
                Type fType = f.parameters.get(i);
                if(!cType.toString().equals(fType.toString())){
                    errors.semError(c,
                        "Expected type `%s`; got type `%s` in parameter %d", fType.toString(), cType.toString(),i);
                }
            }
        }else{
            errors.semError(c,
                "Expected %d arguments; got %d",
                f.parameters.size(),c.args.size());
        }
        return f.returnType;
    }
    @Override
    public Type analyze(MethodCallExpr c){
        FuncType f = (FuncType)c.method.dispatch(this);
        if(f==null){
            return OBJECT_TYPE;
        }
        if(c.args.size() == f.parameters.size()){
            for(int i = 0; i < c.args.size(); i++){
                Type cType = c.args.get(i).dispatch(this);
                Type fType = f.parameters.get(i);
                if(!cType.toString().equals(fType.toString())){
                    errors.semError(c,
                        "Expected type `%s`; got type `%s` in parameter %d", fType.toString(), cType.toString(),i);
                }
            }
        }else{
            errors.semError(c,
                "Expected %d arguments; got %d",
                f.parameters.size(),c.args.size());
        }
        return f.returnType;
    }
    
    @Override
    public Type analyze(MemberExpr m){
        Type t = m.object.dispatch(this);
        ClassDefType cdType = null;
        String cname = "";
        if(t instanceof ClassValueType){
            cname= ((ClassValueType)t).className();
            cdType = (ClassDefType)(globals.get(cname));
        }
        SymbolTable<Type> classScope = cdType.scope;
        if(classScope == null){
                errors.semError(m,
                    "There is no attribute named `%s` in class `%s`", m.member.name, cdType.name
                );
        }else{
            Type mType = classScope.get(m.member.name);
            if(mType==null){
                errors.semError(m,
                    "There is no attribute named `%s` in class `%s`", m.member.name, cdType.name
                );
                
            }
            return mType;

        }

        return null;
    }

    @Override
    public Type analyze(ReturnStmt s){
        if (sym.getParent()==null) {
                    errors.semError(s,
                                "Return statement cannot appear at the top level");
                                return null;
                }
        if(returnType!=null){
            if(s.value == null && !returnType.className().equals("<None>")){
                errors.semError(s,
                    "Expected type `%s`; got type `<None>`", returnType);
            }
            else
            { 
                ValueType temp = (ValueType)s.value.dispatch(this);
                if(!temp.toString().equals(returnType.className())){
                    errors.semError(s,
                        "Expected type `%s`; got type `%s`", returnType, temp);
                }
            }
        }
        return null;
    }

    @Override
    public Type analyze(IntegerLiteral i) {
        return i.setInferredType(Type.INT_TYPE);
    }
    @Override
    public Type analyze(BooleanLiteral i) {
        return i.setInferredType(Type.BOOL_TYPE);
    }
    @Override
    public Type analyze(StringLiteral i) {
        return i.setInferredType(Type.STR_TYPE);
    }
    @Override
    public Type analyze(NoneLiteral i) {
        return i.setInferredType(Type.NONE_TYPE);
    }
    @Override
    public Type analyze(BinaryExpr e) {
        Type t1 = e.left.dispatch(this);
        Type t2 = e.right.dispatch(this);

        switch (e.operator) {
        case "+":
            if (INT_TYPE.equals(t1) && INT_TYPE.equals(t2)) {
                return e.setInferredType(INT_TYPE);
            } else if (STR_TYPE.equals(t1) && STR_TYPE.equals(t2)) {
                return e.setInferredType(STR_TYPE);
            }
            else {
                err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                    e.operator, t1, t2);
                return e.setInferredType(INT_TYPE);
            }
        case "-":
            if (INT_TYPE.equals(t1) && INT_TYPE.equals(t2)) {
                return e.setInferredType(INT_TYPE);
            } else {
                err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                    e.operator, t1, t2);
                return e.setInferredType(INT_TYPE);
            }
        case "*":
            if (INT_TYPE.equals(t1) && INT_TYPE.equals(t2)) {
                return e.setInferredType(INT_TYPE);
            } else {
                err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                    e.operator, t1, t2);
                return e.setInferredType(INT_TYPE);
            }
        case "//":
            if (INT_TYPE.equals(t1) && INT_TYPE.equals(t2)) {
                return e.setInferredType(INT_TYPE);
            } else {
                err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                    e.operator, t1, t2);
                return e.setInferredType(INT_TYPE);
            }
        case "%":
            if (INT_TYPE.equals(t1) && INT_TYPE.equals(t2)) {
                return e.setInferredType(INT_TYPE);
            } else {
                err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                    e.operator, t1, t2);
                return e.setInferredType(INT_TYPE);
            }
        case "==":
        case "!=":
            if (BOOL_TYPE.equals(t1) && BOOL_TYPE.equals(t2)) {
                return e.setInferredType(BOOL_TYPE);
            }
            else if (INT_TYPE.equals(t1) && INT_TYPE.equals(t2)) {
                return e.setInferredType(BOOL_TYPE);
            } else if (STR_TYPE.equals(t1) && STR_TYPE.equals(t2)) {
                return e.setInferredType(BOOL_TYPE);
            }
            else {
                err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                    e.operator, t1, t2);
                return e.setInferredType(BOOL_TYPE);
            }
        case "and":
        case "or":
            if (BOOL_TYPE.equals(t1) && BOOL_TYPE.equals(t2)) {
                return e.setInferredType(BOOL_TYPE);
            }
            else {
                err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                    e.operator, t1, t2);
                return e.setInferredType(BOOL_TYPE);
            }
        case "is":
            if (!t1.isSpecialType() && !t2.isSpecialType()) {
                return e.setInferredType(BOOL_TYPE);
            }
            else {
                err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                    e.operator, t1, t2);
                return e.setInferredType(BOOL_TYPE);
            }
        case "<=":
        case "<":
        case ">":
        case ">=":
            if (INT_TYPE.equals(t1) && INT_TYPE.equals(t2)) {
                return e.setInferredType(BOOL_TYPE);
            } else {
                err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                    e.operator, t1, t2);
                return e.setInferredType(BOOL_TYPE);
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
        Type varType=varDef.var.dispatch(this);
        Type valType=varDef.value.dispatch(this);
        
        if(!varType.toString().equals(valType.toString())){
            errors.semError(varDef,
                "Expected type `%s`; got type `%s`",
                varType, valType);
            return null;
        }
        return varType;
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

    private void checkAndAddDeclarations(List<Declaration> declarations){
        
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

    private boolean methodOverrideCheck(String name, ClassDefType parentClassDefType,FuncType funcType){
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

    private boolean superClassScopeAttrCollide(String name, ClassDefType parentClassDefType, Type declType){
        
        SymbolTable<Type> scope=parentClassDefType.scope;
        if(scope!=null && scope.get(name)!=null && (scope.get(name) instanceof ValueType || declType instanceof ClassValueType)){
            return true;
        }
        return false;
    }

    private boolean methodParamCheck(FuncType funcType){
        List<ValueType> params=funcType.parameters;
        if (params.isEmpty() 
            || !(params.get(0) instanceof ClassValueType) 
            || !((ClassValueType)params.get(0)).toString().equals(inClassName)) {
                return true;
        }
        return false;
    }

    private List<ValueType> checkAndAddParams(List<TypedVar> params){
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

    public boolean containsReturn(List<Stmt> stmts){
        for(Stmt s: stmts){
            if(guaranteesReturn(s)){
                return true;
            }
        }
        return false;
    }

    private boolean guaranteesReturn(Stmt s){
        if(s instanceof ReturnStmt){
            return true;
        }
        if(s instanceof IfStmt){
            IfStmt ifs=((IfStmt)s);
            if(ifs.elseBody.isEmpty()){
                return false;
            }    
            return containsReturn(ifs.thenBody) && containsReturn(ifs.elseBody);
        }
        return false;
    }
}
