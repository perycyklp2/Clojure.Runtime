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

using System;

#if CLR2
using Microsoft.Scripting.Ast;
#else
using System.Linq.Expressions;
#endif
using System.Reflection.Emit;

namespace clojure.lang.CljCompiler.Ast
{
    class ConstantExpr : LiteralExpr
    {
        #region Data

        readonly object _v;
        readonly int _id;

        public override object Val
        {
            get { return _v; }
        }

        #endregion

        #region Ctors

        public ConstantExpr(object v)
        {
            _v = v;
            _id = Compiler.RegisterConstant(v);
        }            

        #endregion

        #region Type mangling

        public override bool HasClrType
        {
            get { return _v.GetType().IsPublic; }
        }

        public override Type ClrType
        {
            get
            {
                return _v.GetType();
            }
        }

        #endregion

        #region Parsing

        public sealed class Parser : IParser
        {

            [System.Diagnostics.CodeAnalysis.SuppressMessage("Microsoft.Performance", "CA1800:DoNotCastUnnecessarily")]
            public Expr Parse(ParserContext pcon, object form)
            {
                object v = RT.second(form);
                if (v == null)
                    return Compiler.NilExprInstance;
                else if (v is Boolean)
                {
                    if ((bool)v)
                        return Compiler.TrueExprInstance;
                    else
                        return Compiler.FalseExprInstance;
                }
                else if (Util.IsNumeric(v))
                    return NumberExpr.Parse(v);
                else if (v is string)
                    return new StringExpr((String)v);
                else if (v is IPersistentCollection && ((IPersistentCollection)v).count() == 0)
                    return new EmptyExpr(v);
                else
                    return new ConstantExpr(v);
            }
        }

        #endregion

        #region Code generation

        public override Expression GenCode(RHC rhc, ObjExpr objx, GenContext context)
        {
            // Java: fn.emitConstant(gen,id)
            //return Expression.Constant(_v);
            return objx.GenConstant(context,_id,_v);
        }

        public override void Emit(RHC rhc, ObjExpr2 objx, GenContext context)
        {
            objx.EmitConstant(context, _id, _v);
            if (rhc == RHC.Statement)
                context.GetILGenerator().Emit(OpCodes.Pop);
        }

        #endregion
    }
}
