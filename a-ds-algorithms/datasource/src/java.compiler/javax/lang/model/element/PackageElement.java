/*
 * Copyright (c) 2005, 2020, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package javax.lang.model.element;

import java.util.List;
import javax.lang.model.type.TypeMirror;

/**
 * Represents a package program element.  Provides access to information
 * about the package and its members.
 *
 * @author Joseph D. Darcy
 * @author Scott Seligman
 * @author Peter von der Ah&eacute;
 * @see javax.lang.model.util.Elements#getPackageOf
 * @since 1.6
 */
public interface PackageElement extends Element, QualifiedNameable {
    /**
     * {@return a {@linkplain javax.lang.model.type.NoType pseudo-type}
     * for this package}
     *
     * @see javax.lang.model.type.NoType
     * @see javax.lang.model.type.TypeKind#PACKAGE
     */
    @Override
    TypeMirror asType();

    /**
     * Returns the fully qualified name of this package.  This is also
     * known as the package's <i>canonical</i> name.  For an
     * {@linkplain #isUnnamed() unnamed package}, an <a
     * href=Name.html#empty_name>empty name</a> is returned.
     *
     * @apiNote The fully qualified name of a named package that is
     * not a subpackage of a named package is its simple name. The
     * fully qualified name of a named package that is a subpackage of
     * another named package consists of the fully qualified name of
     * the containing package, followed by "{@code .}", followed by the simple
     * (member) name of the subpackage.
     *
     * @return the fully qualified name of this package, or an
     * empty name if this is an unnamed package
     * @jls 6.7 Fully Qualified Names and Canonical Names
     */
    Name getQualifiedName();

    /**
     * Returns the simple name of this package.  For an {@linkplain
     * #isUnnamed() unnamed package}, an <a
     * href=Name.html#empty_name>empty name</a> is returned.
     *
     * @return the simple name of this package or an empty name if
     * this is an unnamed package
     */
    @Override
    Name getSimpleName();

    /**
     * {@return the {@linkplain NestingKind#TOP_LEVEL top-level}
     * classes and interfaces within this package}  Note that
     * subpackages are <em>not</em> considered to be enclosed by a
     * package.
     */
    @Override
    List<? extends Element> getEnclosedElements();

    /**
     * {@return {@code true} if this is an unnamed package and {@code
     * false} otherwise}
     *
     * @jls 7.4.2 Unnamed Packages
     */
    boolean isUnnamed();

    /**
     * {@return the enclosing module if such a module exists; otherwise
     * {@code null}}
     *
     * One situation where a module does not exist for a package is if
     * the environment does not include modules, such as an annotation
     * processing environment configured for a {@linkplain
     * javax.annotation.processing.ProcessingEnvironment#getSourceVersion
     * source version} without modules.
     *
     * @revised 9
     */
    @Override
    Element getEnclosingElement();
}
