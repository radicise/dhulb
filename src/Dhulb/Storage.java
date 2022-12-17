package Dhulb;

import Dhulb.Exceptions.CompilationException;
import Dhulb.Exceptions.InternalCompilerException;

interface Storage {
	public void store() throws CompilationException, InternalCompilerException;
	public FullType bring() throws CompilationException, InternalCompilerException;
}