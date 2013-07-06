package rses.inverse.util;





/** An implementation that glues together a user's
 *  fortran or C code so that it can be used by
 *  this java program.
 *
 *  <p>
 *  You can use this class to obtain a 'free' implementation
 *  of UserFunctionHandle if you have existing c/fortran/c++
 *  code that implements the following methods
 *  <ul>
 *  <li> user_init(int* numDimensions, float[][] bounds, float[] scale) </li>
 *  <li> forward(int* numDimensions, float[] model, float* misfit) </li>
 *  </ul>
 *
 *  <p>
 *  This code must be compiled into an object file
 *  called nativeUser.obj   <i>If this file does not exist, this class
 *  will not work </i>.
 *
 *  <p>
 *  This object file must have
 *  entry points <code>user_init_</code> and
 *  <code>forward_</code>. Note the trailing underscore's
 *  -- this is because most fortran compilers will give
 *  a subroutine called <code>user_init</code> an entry point called
 *  <code>user_init_</code>. Most C compilers don't do this,
 *  so if you have a C implementation, you probably need to
 *  call your functions <code>user_init_</code> and
 *  <code>forward_</code>, but this is all just rule-of-thumb,
 *  and you should consult your compiler's documentation if
 *  you are not sure.
 *
 *  <p>
 *  The object file <code>nativeUser.obj</code>can be either a shared (dynamically linked)
 *  library, or a static library. It <i>cannot</i> be just an
 *  archive of object files (created with the UNIX 'ar' utility).
 *  You need to do something like
 *
 *  <blockquote>
 *  Example:
 *
 *  ld -shared *.o -o nativeUser.obj (FOR A SHARED LIBRARY)
 *  </blockquote>
 *
 *  <p>
 *
 *  You can then instantiate an instance of this class by
 *  doing
 *  <code> lufh = new LegacyUserFunctioHandler(PATH_TO_USER_DIRECTORY, PATH_TO_RSES_BASE) </code>.
 *  Where PATH_TO_USER_DIRECTORY is the full path to the directory that holds
 *  the user library and any required input/data files for the user code, and
 *  PATH_TO_RSES_BASE is the full path to the RSES toolkit.
 *
 *  <p>
 *  @author Peter Rickwood
 *
 */




public interface LegacyFunctionHandle extends rses.inverse.UserFunctionHandle
{
}











