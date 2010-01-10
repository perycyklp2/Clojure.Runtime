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
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Reflection;
#if CLR2
using Microsoft.Scripting.Ast;
#else
using System.Linq.Expressions;
#endif
using AstUtils = Microsoft.Scripting.Ast.Utils;
using Microsoft.Scripting;

using System.IO;
using System.Dynamic;

namespace clojure.lang.CljCompiler.Ast
{
    class StaticMethodExpr : MethodExpr
    {
        #region Data

        readonly Type _type;
        //readonly string _methodName;
        //readonly List<HostArg> _args;
        //readonly MethodInfo _method;
        //readonly string _source;
        //readonly IPersistentMap _spanMap;
        //readonly Symbol _tag;

        #endregion

        #region Ctors

        public StaticMethodExpr(string source, IPersistentMap spanMap, Symbol tag, Type type, string methodName, List<HostArg> args)
            : base(source,spanMap,tag,methodName,args)
        {
            _type = type;

            _method  = GetMatchingMethod(spanMap, _type, _args, _methodName);
        }

        #endregion

        #region Type mangling

        public override bool HasClrType
        {
            get { return _method != null || _tag != null; }
        }

        public override Type ClrType
        {
            get { return _tag != null ? Compiler.TagToType(_tag) : _method.ReturnType; }
        }

        #endregion

        #region Code generation

        protected override bool IsStaticCall
        {
            get { return true; }
        }

        protected override Expression GenTargetExpression(GenContext context)
        {
            return Expression.Constant(_type, typeof(Type));
        }

        public override Expression GenDlrUnboxed(GenContext context)
        {
            if (_method != null)
            {
                Expression call = GenDlrForMethod(context);
                call = Compiler.MaybeAddDebugInfo(call, _spanMap);
                return call;
            }
            else
                throw new InvalidOperationException("Unboxed emit of unknown member.");
        }

        protected override Expression GenDlrForMethod(GenContext context)
        {
            Expression[] args = GenTypedArgs(context, _method.GetParameters(), _args);
            return AstUtils.ComplexCallHelper(_method, args);
        }

        #endregion
    }
}
