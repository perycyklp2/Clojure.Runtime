﻿/**
 *   Copyright (c) Rich Hickey. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file epl-v10.html at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

/**
 *   Author: David Miller
 **/

#if CLR2
extern alias MSC;
#endif

using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;
#if CLR2
using Microsoft.Scripting.Ast;
#else
using System.Linq.Expressions;
#endif

using System.Dynamic;
using Microsoft.Scripting.Actions.Calls;
using Microsoft.Scripting.Actions;
using Microsoft.Scripting.Runtime;

namespace clojure.lang.CljCompiler.Ast
{
    public abstract class HostExpr : Expr, MaybePrimitiveExpr
    {
        #region Symbols

        public static readonly Symbol BY_REF = Symbol.intern("by-ref");
        public static readonly Symbol TYPE_ARGS = Symbol.intern("type-args");

        #endregion

        #region Parsing

        public sealed class Parser : IParser
        {
            public Expr Parse(ParserContext pcon, object form)
            {
                ISeq sform = (ISeq)form;

                // form is one of:
                //  (. x fieldname-sym)
                //  (. x 0-ary-method)
                //  (. x propertyname-sym)
                //  (. x methodname-sym args)+
                //  (. x (methodname-sym args?))
                //  (. x (generic-m 

                if (RT.Length(sform) < 3)
                    throw new ArgumentException("Malformed member expression, expecting (. target member ... )");

                string source = (string)Compiler.SourceVar.deref();
                IPersistentMap spanMap = (IPersistentMap)Compiler.SourceSpanVar.deref();  // Compiler.GetSourceSpanMap(form);

                Symbol tag = Compiler.TagOf(sform);

                // determine static or instance
                // static target must be symbol, either fully.qualified.Typename or Typename that has been imported
                 
                Type t = HostExpr.MaybeType(RT.second(sform), false);
                // at this point, t will be non-null if static

                Expr instance = null;
                if (t == null)
                    instance = Compiler.Analyze(pcon.EvEx(),RT.second(sform));

                bool isZeroArityCall = RT.Length(sform) == 3 && (RT.third(sform) is Symbol || RT.third(sform) is Keyword);

                if (isZeroArityCall)
                {
                    PropertyInfo pinfo = null;
                    FieldInfo finfo = null;
                    MethodInfo minfo = null;

                    Symbol sym = (RT.third(sform) is Keyword) ? ((Keyword)RT.third(sform)).Symbol : (Symbol)RT.third(sform);
                    string fieldName = Compiler.munge(sym.Name);
                    // The JVM version does not have to worry about Properties.  It captures 0-arity methods under fields.
                    // We have to put in special checks here for this.
                    // Also, when reflection is required, we have to capture 0-arity methods under the calls that
                    //   are generated by StaticFieldExpr and InstanceFieldExpr.
                    if (t != null)
                    {
                        if ((finfo = Reflector.GetField(t, fieldName, true)) != null)
                            return new StaticFieldExpr(source, spanMap, tag, t, fieldName, finfo);
                        if ((pinfo = Reflector.GetProperty(t, fieldName, true)) != null)
                            return new StaticPropertyExpr(source, spanMap, tag, t, fieldName, pinfo);
                        if ((minfo = Reflector.GetArityZeroMethod(t, fieldName, true)) != null)
                            return new StaticMethodExpr(source, spanMap, tag, t, fieldName, null, new List<HostArg>());
                        throw new MissingMemberException(t.Name, fieldName);
                    }
                    else if (instance != null && instance.HasClrType && instance.ClrType != null)
                    {
                        Type instanceType = instance.ClrType;
                        if ((finfo = Reflector.GetField(instanceType, fieldName, false)) != null)
                            return new InstanceFieldExpr(source, spanMap, tag, instance, fieldName, finfo);
                        if ((pinfo = Reflector.GetProperty(instanceType, fieldName, false)) != null)
                            return new InstancePropertyExpr(source, spanMap, tag, instance, fieldName, pinfo);
                        if ((minfo = Reflector.GetArityZeroMethod(instanceType, fieldName, false)) != null)
                            return new InstanceMethodExpr(source, spanMap, tag, instance, fieldName, null, new List<HostArg>());
                        if (pcon.IsAssignContext)
                            return new InstanceFieldExpr(source, spanMap, tag, instance, fieldName, null); // same as InstancePropertyExpr when last arg is null
                        else
                            return new InstanceZeroArityCallExpr(source, spanMap, tag, instance, fieldName);
                    }
                    else
                    {
                        //  t is null, so we know this is not a static call
                        //  If instance is null, we are screwed anyway.
                        //  If instance is not null, then we don't have a type.
                        //  So we must be in an instance call to a property, field, or 0-arity method.
                        //  The code generated by InstanceFieldExpr/InstancePropertyExpr with a null FieldInfo/PropertyInfo
                        //     will generate code to do a runtime call to a Reflector method that will check all three.
                        //return new InstanceFieldExpr(source, spanMap, tag, instance, fieldName, null); // same as InstancePropertyExpr when last arg is null
                        //return new InstanceZeroArityCallExpr(source, spanMap, tag, instance, fieldName); 
                        if (pcon.IsAssignContext)
                            return new InstanceFieldExpr(source, spanMap, tag, instance, fieldName, null); // same as InstancePropertyExpr when last arg is null
                        else
                            return new InstanceZeroArityCallExpr(source, spanMap, tag, instance, fieldName); 

                    }
                }
 
                //ISeq call = RT.third(form) is ISeq ? (ISeq)RT.third(form) : RT.next(RT.next(form));

                ISeq call;
                List<Type> typeArgs = null;
 
               //object third = RT.third(form);

                //if (third is ISeq && RT.first(third) is Symbol && ((Symbol)RT.first(third)).Equals(GENERIC))
                //{
                //    // We have a generic method call
                //    // (. thing (generic methodname type1 ...) args...)
                //    typeArgs = ParseGenericMethodTypeArgs(RT.next(RT.next(third)));
                //    call = RT.listStar(RT.second(third), RT.next(RT.next(RT.next(form))));
                //}
                //else
                //    call = RT.third(form) is ISeq ? (ISeq)RT.third(form) : RT.next(RT.next(form));

                object fourth = RT.fourth(sform);
                if (fourth is ISeq && RT.first(fourth) is Symbol && ((Symbol)RT.first(fourth)).Equals(TYPE_ARGS))
                 {
                    // We have a type args supplied for a generic method call
                    // (. thing methodname (type-args type1 ... ) args ...)
                    typeArgs = ParseGenericMethodTypeArgs(RT.next(fourth));
                    call = RT.listStar(RT.third(sform), RT.next(RT.next(RT.next(RT.next(sform)))));
                }
                else
                    call = RT.third(sform) is ISeq ? (ISeq)RT.third(sform) : RT.next(RT.next(sform));

                if (!(RT.first(call) is Symbol))
                    throw new ArgumentException("Malformed member exception");

                string methodName = Compiler.munge(((Symbol)RT.first(call)).Name);

                List<HostArg> args = ParseArgs(pcon, RT.next(call));

                return t != null
                    ? (MethodExpr)(new StaticMethodExpr(source, spanMap, tag, t, methodName, typeArgs, args))
                    : (MethodExpr)(new InstanceMethodExpr(source, spanMap, tag, instance, methodName, typeArgs, args));
            }
        }

        static List<Type> ParseGenericMethodTypeArgs(ISeq targs)
        {
            List<Type> types = new List<Type>();

            for (ISeq s = targs; s != null; s = s.next())
            {
                object arg = s.first();
                if (!(arg is Symbol))
                    throw new ArgumentException("Malformed generic method designator: type arg must be a Symbol");
                Type t = HostExpr.MaybeType(arg, false);
                if (t == null)
                    throw new ArgumentException("Malformed generic method designator: invalid type arg");
                types.Add(t);
            }

            return types;
        }

        internal static List<HostArg> ParseArgs(ParserContext pcon, ISeq argSeq)
        {
            List<HostArg> args = new List<HostArg>();

            for (ISeq s = argSeq; s != null; s = s.next())
            {
                object arg = s.first();

                HostArg.ParameterType paramType = HostArg.ParameterType.Standard;
                LocalBinding lb = null;

                ISeq argAsSeq = arg as ISeq;
                if (argAsSeq != null)
                {
                    Symbol op = RT.first(argAsSeq) as Symbol;
                    if (op != null && op.Equals(BY_REF))
                    {
                        if (RT.Length(argAsSeq) != 2)
                            throw new ArgumentException("Wrong number of arguments to {0}", op.Name);

                        object localArg = RT.second(argAsSeq);
                        Symbol symLocalArg = localArg as Symbol;
                        if (symLocalArg == null || (lb = Compiler.ReferenceLocal(symLocalArg)) == null)
                            throw new ArgumentException("Argument to {0} must be a local variable.", op.Name);

                        paramType = HostArg.ParameterType.ByRef;

                        arg = localArg;
                    }
                }

                Expr expr = Compiler.Analyze(pcon.EvEx(),arg);

                args.Add(new HostArg(paramType, expr, lb));
            }

            return args;

        }

        #endregion

        #region Expr Members

        public abstract bool HasClrType { get; }
        public abstract Type ClrType { get; }
        public abstract object Eval();
        public abstract Expression GenCode(RHC rhc, ObjExpr objx, GenContext context);

        #endregion

        #region MaybePrimitiveExpr 

        public abstract bool CanEmitPrimitive { get; }
        public abstract Expression GenCodeUnboxed(RHC rhc, ObjExpr objx, GenContext context);

        #endregion

        #region Reflection helpers

        internal static Expression[] GenTypedArgs(ObjExpr objx, GenContext context, ParameterInfo[] parms, List<HostArg> args)
        {
            Expression[] exprs = new Expression[parms.Length];
            for (int i = 0; i < parms.Length; i++)
                exprs[i] = GenTypedArg(objx, context, parms[i].ParameterType, args[i].ArgExpr);
            return exprs;
        }

        internal static Expression[] GenTypedArgs(ObjExpr objx, GenContext context, Type[] paramTypes, IPersistentVector args)
        {
            Expression[] exprs = new Expression[paramTypes.Length];
            for (int i = 0; i < paramTypes.Length; i++)
                exprs[i] = GenTypedArg(objx, context, paramTypes[i], (Expr)args.nth(i));
            return exprs;
        }


        [System.Diagnostics.CodeAnalysis.SuppressMessage("Microsoft.Performance", "CA1800:DoNotCastUnnecessarily")]
        internal static Expression GenTypedArg(ObjExpr objx, GenContext context, Type paramType, Expr arg)
        {
            Type primt = Compiler.MaybePrimitiveType(arg);

            if ( primt == paramType )
            {
                Expression expr = ((MaybePrimitiveExpr)arg).GenCodeUnboxed(RHC.Expression, objx, context);
                return expr;
            }
            else if ( primt == typeof(int) && paramType == typeof(long) )
            {
                Expression expr = ((MaybePrimitiveExpr)arg).GenCodeUnboxed(RHC.Expression, objx, context);
                expr = Expression.Convert(expr,typeof(long));
                return expr;
            }
            else if ( primt == typeof(long) && paramType == typeof(int) )
            {
                Expression expr = ((MaybePrimitiveExpr)arg).GenCodeUnboxed(RHC.Expression, objx, context);
                if (RT.booleanCast(RT.UNCHECKED_MATH.deref()))
                    expr = Expression.Call(Compiler.Method_RT_uncheckedIntCast_long, expr);
                else
                    expr = Expression.Call(Compiler.Method_RT_intCast_long, expr);
                return expr;
            }
            else if ( primt == typeof(float) && paramType == typeof(double) )
            {
                Expression expr = ((MaybePrimitiveExpr)arg).GenCodeUnboxed(RHC.Expression, objx, context);
                expr = Expression.Convert(expr,typeof(double));
                return expr;
            }
            else if ( primt == typeof(double) && paramType == typeof(float) )
            {
                Expression expr = ((MaybePrimitiveExpr)arg).GenCodeUnboxed(RHC.Expression, objx, context);
                expr = Expression.Convert(expr,typeof(float));
                return expr;
            }
            else
            {
                Expression argExpr = arg.GenCode(RHC.Expression, objx, context);
                return GenUnboxArg(argExpr, paramType);
            }
        }

        //internal static readonly MethodInfo Method_Util_ConvertToSByte = typeof(Util).GetMethod("ConvertToByte");
        //internal static readonly MethodInfo Method_Util_ConvertToByte = typeof(Util).GetMethod("ConvertToByte");
        //internal static readonly MethodInfo Method_Util_ConvertToShort = typeof(Util).GetMethod("ConvertToShort");
        //internal static readonly MethodInfo Method_Util_ConvertToUShort = typeof(Util).GetMethod("ConvertToUShort");
        //internal static readonly MethodInfo Method_Util_ConvertToInt = typeof(Util).GetMethod("ConvertToInt");
        //internal static readonly MethodInfo Method_Util_ConvertToUInt = typeof(Util).GetMethod("ConvertToUInt");
        //internal static readonly MethodInfo Method_Util_ConvertToLong = typeof(Util).GetMethod("ConvertToLong");
        //internal static readonly MethodInfo Method_Util_ConvertToULong = typeof(Util).GetMethod("ConvertToULong");
        //internal static readonly MethodInfo Method_Util_ConvertToFloat = typeof(Util).GetMethod("ConvertToFloat");
        //internal static readonly MethodInfo Method_Util_ConvertToDouble = typeof(Util).GetMethod("ConvertToDouble");
        //internal static readonly MethodInfo Method_Util_ConvertToChar = typeof(Util).GetMethod("ConvertToChar");
        //internal static readonly MethodInfo Method_Util_ConvertToDecimal = typeof(Util).GetMethod("ConvertToDecimal");

        internal static readonly MethodInfo Method_RT_sbyteCast = typeof(RT).GetMethod("sbyteCast", new Type[] { typeof(object) });
        internal static readonly MethodInfo Method_RT_byteCast = typeof(RT).GetMethod("byteCast", new Type[] { typeof(object) });
        internal static readonly MethodInfo Method_RT_shortCast = typeof(RT).GetMethod("shortCast", new Type[] { typeof(object) });
        internal static readonly MethodInfo Method_RT_ushortCast = typeof(RT).GetMethod("ushortCast", new Type[] { typeof(object) });
        internal static readonly MethodInfo Method_RT_intCast = typeof(RT).GetMethod("intCast", new Type[] { typeof(object) });
        internal static readonly MethodInfo Method_RT_uintCast = typeof(RT).GetMethod("uintCast", new Type[] { typeof(object) });
        internal static readonly MethodInfo Method_RT_longCast = typeof(RT).GetMethod("longCast", new Type[] { typeof(object) });
        internal static readonly MethodInfo Method_RT_ulongCast = typeof(RT).GetMethod("ulongCast", new Type[] { typeof(object) });
        internal static readonly MethodInfo Method_RT_floatCast = typeof(RT).GetMethod("floatCast", new Type[] { typeof(object) });
        internal static readonly MethodInfo Method_RT_doubleCast = typeof(RT).GetMethod("doubleCast", new Type[] { typeof(object) });
        internal static readonly MethodInfo Method_RT_charCast = typeof(RT).GetMethod("charCast", new Type[] { typeof(object) });
        internal static readonly MethodInfo Method_RT_decimalCast = typeof(RT).GetMethod("decimalCast", new Type[] { typeof(object) });

        internal static readonly MethodInfo Method_RT_uncheckedSbyteCast = typeof(RT).GetMethod("uncheckedSByteCast", new Type[] { typeof(object) });
        internal static readonly MethodInfo Method_RT_uncheckedByteCast = typeof(RT).GetMethod("uncheckedByteCast", new Type[] { typeof(object) });
        internal static readonly MethodInfo Method_RT_uncheckedShortCast = typeof(RT).GetMethod("uncheckedShortCast", new Type[] { typeof(object) });
        internal static readonly MethodInfo Method_RT_uncheckedUshortCast = typeof(RT).GetMethod("uncheckedUShortCast", new Type[] { typeof(object) });
        internal static readonly MethodInfo Method_RT_uncheckedIntCast = typeof(RT).GetMethod("uncheckedIntCast", new Type[] { typeof(object) });
        internal static readonly MethodInfo Method_RT_uncheckedUintCast = typeof(RT).GetMethod("uncheckedUIntCast", new Type[] { typeof(object) });
        internal static readonly MethodInfo Method_RT_uncheckedLongCast = typeof(RT).GetMethod("uncheckedLongCast", new Type[] { typeof(object) });
        internal static readonly MethodInfo Method_RT_uncheckedUlongCast = typeof(RT).GetMethod("uncheckedULongCast", new Type[] { typeof(object) });
        internal static readonly MethodInfo Method_RT_uncheckedFloatCast = typeof(RT).GetMethod("uncheckedFloatCast", new Type[] { typeof(object) });
        internal static readonly MethodInfo Method_RT_uncheckedDoubleCast = typeof(RT).GetMethod("uncheckedDoubleCast", new Type[] { typeof(object) });
        internal static readonly MethodInfo Method_RT_uncheckedCharCast = typeof(RT).GetMethod("uncheckedCharCast", new Type[] { typeof(object) });
        internal static readonly MethodInfo Method_RT_uncheckedDecimalCast = typeof(RT).GetMethod("uncheckedDecimalCast", new Type[] { typeof(object) });

        internal static readonly MethodInfo Method_RT_booleanCast = typeof(RT).GetMethod("booleanCast", new Type[] { typeof(object) });

        internal static Expression GenUnboxArg(Expression argExpr, Type paramType)
        {

            Type argType = argExpr.Type;

            if (argType == paramType)
                return argExpr;

            if (paramType == typeof(void))
                return Expression.Block(argExpr, Expression.Empty());

            if (argExpr.Type == typeof(void))
                return Expression.Block(argExpr, Expression.Default(paramType));

            //if (paramType.IsAssignableFrom(argType))
            //    return argExpr;

            if (paramType.IsPrimitive)
            {
                Expression objArgExpr = Expression.Convert(argExpr,typeof(object));

                if (paramType == typeof(bool))
                    return Expression.Call(null, Method_RT_booleanCast, objArgExpr);
                //if (Util.IsPrimitiveNumeric(argType) && Util.IsPrimitiveNumeric(paramType))
                //    return Expression.Convert(argExpr,paramType);
                if (RT.booleanCast(RT.UNCHECKED_MATH.deref()))
                {
                    if (paramType == typeof(sbyte))
                        return Expression.Call(null, Method_RT_uncheckedSbyteCast, objArgExpr);
                    else if (paramType == typeof(byte))
                        return Expression.Call(null, Method_RT_uncheckedByteCast, objArgExpr);
                    else if (paramType == typeof(short))
                        return Expression.Call(null, Method_RT_uncheckedShortCast, objArgExpr);
                    else if (paramType == typeof(ushort))
                        return Expression.Call(null, Method_RT_uncheckedUshortCast, objArgExpr);
                    else if (paramType == typeof(int))
                        return Expression.Call(null, Method_RT_uncheckedIntCast, objArgExpr);
                    else if (paramType == typeof(uint))
                        return Expression.Call(null, Method_RT_uncheckedUintCast, objArgExpr);
                    else if (paramType == typeof(long))
                        return Expression.Call(null, Method_RT_uncheckedLongCast, objArgExpr);
                    else if (paramType == typeof(ulong))
                        return Expression.Call(null, Method_RT_uncheckedUlongCast, objArgExpr);
                    else if (paramType == typeof(float))
                        return Expression.Call(null, Method_RT_uncheckedFloatCast, objArgExpr);
                    else if (paramType == typeof(double))
                        return Expression.Call(null, Method_RT_uncheckedDoubleCast, objArgExpr);
                    else if (paramType == typeof(char))
                        return Expression.Call(null, Method_RT_uncheckedCharCast, objArgExpr);
                    else if (paramType == typeof(decimal))
                        return Expression.Call(null, Method_RT_uncheckedDecimalCast, objArgExpr);
                }
                else
                {
                    if (paramType == typeof(sbyte))
                        return Expression.Call(null, Method_RT_sbyteCast, objArgExpr);
                    else if (paramType == typeof(byte))
                        return Expression.Call(null, Method_RT_byteCast, objArgExpr);
                    else if (paramType == typeof(short))
                        return Expression.Call(null, Method_RT_shortCast, objArgExpr);
                    else if (paramType == typeof(ushort))
                        return Expression.Call(null, Method_RT_ushortCast, objArgExpr);
                    else if (paramType == typeof(int))
                        return Expression.Call(null, Method_RT_intCast, objArgExpr);
                    else if (paramType == typeof(uint))
                        return Expression.Call(null, Method_RT_uintCast, objArgExpr);
                    else if (paramType == typeof(long))
                        return Expression.Call(null, Method_RT_longCast, objArgExpr);
                    else if (paramType == typeof(ulong))
                        return Expression.Call(null, Method_RT_ulongCast, objArgExpr);
                    else if (paramType == typeof(float))
                        return Expression.Call(null, Method_RT_floatCast, objArgExpr);
                    else if (paramType == typeof(double))
                        return Expression.Call(null, Method_RT_doubleCast, objArgExpr);
                    else if (paramType == typeof(char))
                        return Expression.Call(null, Method_RT_charCast, objArgExpr);
                    else if (paramType == typeof(decimal))
                        return Expression.Call(null, Method_RT_decimalCast, objArgExpr);
                }
            }
            
            return Expression.Convert(argExpr,paramType);
        }

        #endregion

        #region Code gen helpers

        public static Expression GenBoxReturn(Expression expr, Type returnType, ObjExpr objx, GenContext context)
        {
            if (returnType == typeof(void))
                return Expression.Block(expr, Compiler.NIL_EXPR.GenCode(RHC.Expression,objx,context));

            if (returnType.IsPrimitive)
            {
                Expression toConv;

                if (returnType == typeof(float))
                    toConv = Expression.Convert(expr, typeof(float));
                else if (returnType == typeof(int))
                    toConv = Expression.Convert(expr, typeof(long));
                else
                    toConv = expr;

                return Expression.Convert(toConv, typeof(Object));
            }

            return expr;
        }

        #endregion

        #region Tags and types

        [System.Diagnostics.CodeAnalysis.SuppressMessage("Microsoft.Performance", "CA1800:DoNotCastUnnecessarily")]
        public static Type MaybeType(object form, bool stringOk)
        {
            if (form is Type)
                return (Type)form;

            Type t = null;
            if (form is Symbol)
            {
                Symbol sym = (Symbol)form;
                if (sym.Namespace == null) // if ns-qualified, can't be classname
                {
                    if (Util.equals(sym, Compiler.CompileStubSymVar.get()))
                        return (Type)Compiler.CompileStubClassVar.get();
                    // TODO:  This uses Java  [whatever  notation.  Figure out what to do here.
                    if (sym.Name.IndexOf('.') > 0 || sym.Name[sym.Name.Length-1] == ']')
                        t = RT.classForName(sym.Name);
                    else
                    {
                        object o = Compiler.CurrentNamespace.GetMapping(sym);
                        if (o is Type)
                            t = (Type)o;
                        else
                        {
                            try
                            {
                                t = RT.classForName(sym.Name);
                            }
                            catch (Exception)
                            {
                                //aargh
                            }
                        }
                    }

                }
            }
            else if (stringOk && form is string)
                t = RT.classForName((string)form);

            return t;
        }

        internal static Type TagToType(object tag)
        {
            Type t = MaybeType(tag, true);

            Symbol sym = tag as Symbol;
            if (sym != null)
            {
                if (sym.Namespace == null) // if ns-qualified, can't be classname
                {
                    switch (sym.Name)
                    {
                        case "objects": t = typeof(object[]); break;
                        case "ints": t = typeof(int[]); break;
                        case "longs": t = typeof(long[]); break;
                        case "floats": t = typeof(float[]); break;
                        case "doubles": t = typeof(double[]); break;
                        case "chars": t = typeof(char[]); break;
                        case "shorts": t = typeof(short[]); break;
                        case "bytes": t = typeof(byte[]); break;
                        case "booleans":
                        case "bools": t = typeof(bool[]); break;
                        case "uints": t = typeof(uint[]); break;
                        case "ushorts": t = typeof(ushort[]); break;
                        case "ulongs": t = typeof(ulong[]); break;
                        case "sbytes": t = typeof(sbyte[]); break;
                    }
                }
            }
            //else if (tag is String)
            //{
            //    // TODO: This is no longer in the Java version.  SHould we get rid of?
            //    string strTag = (string)tag;
            //    t = Type.GetType(strTag);
            //}

            if (t != null)
                return t;

            throw new ArgumentException("Unable to resolve typename: " + tag);
        }

        #endregion
    }
}
