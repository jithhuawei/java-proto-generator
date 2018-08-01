package com.lloyd.JavaToProto;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * Copyright - Lloyd Sparkes 2012
 * LICENSE: Public Domain - do as you wish, just retain this message.
 * 				I just ask that if you find bugs or improve the code, you raise a push request or an issue, so i can update the code for everyone
 * DISCLAIMER: I am not responsible for your usage of this code, or for any bugs or issues with this code or any resulting side effects
 * 
 * This class simply takes a POJO and creates the associated Proto Buffer Specification File.
 *  
 * Supports:
 * 		Nested POJO's
 * 		Enums
 * 		Arrays/Collections/Lists/Sets (BUT only if they have a type specifier!! (so List<Byte> is supported, List is not supported)
 * 		Maps/KeyValuePairs (BUT they need a type specifier!! (so Map<String,Integer> is supported, Map is not supported)
 * 		Primitives
 * 		Boxed Primitives 
 * 
 * Does Not Support:
 * 		Nested Collections e.g. Map<List<String>,String>
 * 		Arrays with more than one Dimension
 * 
 * Usage - CLI:
 * 		java -jar JavaToProto.jar JavaToProto <class name> [<output file name>]
 * 
 * 		If output file name is not specified the output will be to the console.
 * 
 * 		Ensure that the class name is in the class path somewhere.
 * 
 * Usage - Code:
 * 		
 * 		JavaToProto jpt = new JavaToProto(class name);
 * 		String protoFile = jpt.toString();	
 * 
 * @author Lloyd Sparkes
 */

public class JavaToProto {
	
	private static String NAME = "JavaToProto Generator";
    private static String VERSION = "v0.2";
	
	private static String OPEN_BLOCK = "{";
	private static String CLOSE_BLOCK = "}";
	private static String MESSAGE = "message";
	private static String ENUM = "enum";
	private static String NEWLINE = "\n";
	private static String TAB = "\t";	
	private static String COMMENT = "//";
	private static String SPACE = " ";
	private static String PATH_SEPERATOR = ".";
	private static String REPEATED = "repeated";
	private static String LINE_END = ";";

    private static String SYNTAX= "syntax";
    private static String PROTO= "\"proto3\"";
    private static String PACKAGE="package";
    private static String JAVA_PACKAGE="java_package";
    private static String OUTER_CLASS="java_outer_classname";
    private static String OPTION = "option";
    private static String JAVA_MULTIPLE_FILES="java_multiple_files";
    private static String TRUE="true";

	private StringBuilder builder;
	private Stack<Class> classStack = new Stack<Class>();
	private Map<Class, String> typeMap = getPrimitivesMap();
	private int tabDepth = 0;
    private Stack<Class> fieldStack = new Stack<Class>();
    private static Class origClass;

	/**
	 * Entry Point for the CLI Interface to this Program.
	 * @param args
	 */
	public static void main(String[] args) {
		
		if(args.length == 0){
            System.out.println(
                    "Usage: \n\tjava -jar JavaToProto.jar JavaToProto <class name> [<output file name>]\n");
		}
		
		Class clazz;
		
		try {
			clazz = Class.forName(args[0]);
            origClass = clazz;
		} catch (Exception e) {
			System.out.println("Could not load class. Make Sure it is in the classpath!!");
			e.printStackTrace();
			return;
		}
		
		JavaToProto jtp = new JavaToProto(clazz);
		
		String protoFile = jtp.toString();
		
		if(args.length == 2){
			//Write to File
			
			try{
				File f = new File(args[1]);
				FileWriter fw = new FileWriter(f);
				BufferedWriter out = new BufferedWriter(fw);
				out.write(protoFile);
				out.flush();
				out.close();
			} catch (Exception e) {
                System.out.println(
                        "Got Exception while Writing to File - See Console for File Contents");
				System.out.println(protoFile);
				e.printStackTrace();
			}
			
		} else {
			//Write to Console
			System.out.println(protoFile);
		}
		
	}
	
	/**
	 * Creates a new Instance of JavaToProto to process the given class
	 * @param classToProcess - The Class to be Processed - MUST NOT BE NULL!
	 */
	public JavaToProto(Class classToProcess){
		if(classToProcess == null){
            throw new RuntimeException(
                    "You gave me a null class to process. This cannot be done, please pass in an instance of Class");
		}
		classStack.push(classToProcess);
	}
	
	//region Helper Functions
	
	public String getTabs(){
		String res = "";
		
		for(int i = 0; i < tabDepth; i++){
			res = res + TAB;
		}
		
		return res;
	}
	
	public String getPath(){
		String path = "";
		
		Stack<Class> tStack = new Stack<Class>();
		
		while(!classStack.isEmpty()) {
			Class t = classStack.pop();
			if(path.length() == 0){
				path = t.getSimpleName();
			} else {
				path = t.getSimpleName() + PATH_SEPERATOR + path;
			}
			tStack.push(t);
		}
		
		while(!tStack.isEmpty()){
			classStack.push(tStack.pop());
		}
		
		return path;
	}
	
	public Class currentClass(){
		return classStack.peek();
	}
	
	public Map<Class,String> getPrimitivesMap(){
		Map<Class, String> results = new HashMap<Class, String>();
		
		results.put(double.class, "double");
		results.put(float.class, "float");
		results.put(int.class, "sint32");
		results.put(long.class, "sint64");
		results.put(boolean.class, "bool");
		results.put(byte.class, "bytes");
		results.put(Double.class, "double");
		results.put(Float.class, "float");
		results.put(Integer.class, "sint32");
		results.put(Long.class, "sint64");
		results.put(Boolean.class, "bool");
		results.put(Byte.class, "bytes");
		results.put(String.class, "string");
		
		return results;
	}
	
	public void processField(String type, String name, int index){
        builder.append(getTabs())
                .append(type)
                .append(SPACE)
                .append(name)
                .append(SPACE)
                .append("=")
                .append(SPACE)
                .append(index)
                .append(LINE_END)
                .append(NEWLINE);
	}
	
	public void processRepeatedField(String repeated, String type, String name, int index){
        builder.append(getTabs())
                .append(repeated)
                .append(SPACE)
                .append(type)
                .append(SPACE)
                .append(name)
                .append(SPACE)
                .append("=")
                .append(SPACE)
                .append(index)
                .append(LINE_END)
                .append(NEWLINE);
	}
	
	//end region
	
	private String buildMessage(){
		
		/*if(currentClass().isInterface() || currentClass().isEnum() || Modifier.isAbstract(currentClass().getModifiers())){
			throw new RuntimeException("A Message cannot be an Interface, Abstract OR an Enum");
		}*/
		
		String messageName = currentClass().getSimpleName();
		
		typeMap.put(currentClass(), getPath());
		
        builder.append(getTabs())
                .append(MESSAGE)
                .append(SPACE)
                .append(messageName)
                .append(OPEN_BLOCK)
                .append(NEWLINE);
		
		tabDepth++;
		
		processFields();
		
		tabDepth--;
		
		return messageName;		
	}
	
    private void buildClassMethod() {
        builder.append(NEWLINE);
        String messageName = currentClass().getSimpleName();
        Method[] methods = currentClass().getDeclaredMethods();

        for (Method m : methods) {
            Class<?>[] paramType = m.getParameterTypes();

            // Need to create protobuf definitions only for class methods with parameters.
            if (paramType.length == 0) {
                continue;
            }

            builder.append(getTabs())
                    .append(MESSAGE)
                    .append(SPACE)
                    .append(m.getName())
                    .append(OPEN_BLOCK)
                    .append(NEWLINE);

            tabDepth++;

            Parameter[] parameters = m.getParameters();
            List<String> paramNames = new ArrayList<String>();
            for (Parameter param : parameters) {
                paramNames.add(param.getName());
            }

            for (int i = 0; i < paramType.length; i++) {
                // Check whether Method Parameter is available in the typeMap.
                if (typeMap.get(paramType[i]) != null) {
                    processField(typeMap.get(paramType[i]), paramNames.get(i), i+1);
                }
                // Check whether Method Parameter is an array.
                else if (paramType[i].isArray()) {
                        Class innerType = paramType[i].getComponentType();
                        if (!typeMap.containsKey(innerType)) {
                            fieldStack.push(innerType);
                        }
                        processRepeatedField(REPEATED, typeMap.get(innerType), paramNames.get(i), i + 1);
                        continue;
                }
                // Handle unknown Method Parameters.
                else {
                    processField(paramType[i].getTypeName(), paramNames.get(i), i+1);
                }
            }

            tabDepth--;
            builder.append(getTabs()).append(CLOSE_BLOCK).append(NEWLINE);
        }
    }
	private void processFields(){
		Field[] fields = currentClass().getDeclaredFields();
		
		int i = 0;
		
		for(Field f : fields){
			i++;
			
			int mod = f.getModifiers();
			if(Modifier.isAbstract(mod) || Modifier.isTransient(mod)){
				//Skip this field
				continue;
			}
			
			Class fieldType = f.getType();
			
			//Primitives or Types we have come across before
			if(typeMap.containsKey(fieldType)){
				processField(typeMap.get(fieldType), f.getName(), i);
				continue;
			}
			
            else if (fieldType.isEnum()) {
				processEnum(fieldType);
				processField(typeMap.get(fieldType), f.getName(), i);
				continue;
			}
			
            else if (Map.class.isAssignableFrom(fieldType)) {
				Class innerType = null;
				Class innerType2 = null;
				String entryName = "Map_" + f.getName();
				
				Type t = f.getGenericType();
				
				if(t instanceof ParameterizedType){
					ParameterizedType tt = (ParameterizedType)t;
					innerType = (Class) tt.getActualTypeArguments()[0];
					innerType2 = (Class) tt.getActualTypeArguments()[1];	
					buildEntryType(entryName, innerType, innerType2);
				}
				
				processRepeatedField(REPEATED,entryName, f.getName(), i);
				continue;
			}
			
            else if (fieldType.isArray()) {
				Class innerType = fieldType.getComponentType();
				if(!typeMap.containsKey(innerType)){
                    buildNestedType(innerType, i);
                    fieldStack.push(innerType);
				}
				processRepeatedField(REPEATED, typeMap.get(innerType), f.getName(), i);
				continue;
			}
			
            // Check for List
            else if (fieldType.equals(List.class)) {
                Type type = f.getGenericType();
                Class listParamType = null;
                if (type instanceof ParameterizedType) {
                    ParameterizedType pt = (ParameterizedType) type;
                    listParamType = (Class) pt.getActualTypeArguments()[0];
                }
                // Check whether the List parameter is a primitive type or User-defined type.
                if (!typeMap.containsKey(listParamType)) {
                    fieldStack.push(listParamType);
                    processRepeatedField(REPEATED, listParamType.getSimpleName(), f.getName(), i);
                }
                else {
                processRepeatedField(REPEATED, typeMap.get(listParamType), f.getName(), i);
                }
                continue;
            }

            else if (Collection.class.isAssignableFrom(fieldType)) {
				Class innerType = null;
				
				Type t = f.getGenericType();
				
				if(t instanceof ParameterizedType){
					ParameterizedType tt = (ParameterizedType)t;
					innerType = (Class) tt.getActualTypeArguments()[0];
				}
				
				if(!typeMap.containsKey(innerType)){
                    buildNestedType(innerType, i);
                    fieldStack.push(innerType);
				}
				processRepeatedField(REPEATED,typeMap.get(fieldType), f.getName(), i);
				continue;
			}
			
            // Ok so not a primitive / scalar, not a map or collection, and we havnt already
            // processed it
            else{
				//So it must be another pojo
            	buildNestedType(fieldType, i);
                fieldStack.push(fieldType);
                continue;
		}
        }
        tabDepth--;
        builder.append(getTabs()).append(CLOSE_BLOCK).append(NEWLINE);
        build();
	}
	
    private void build() {
		while(!fieldStack.isEmpty()) {
			Class pop = fieldStack.pop();
			classStack.pop();
			classStack.push(pop);
		buildMessage();
		}
	}

	private void buildNestedType(Class type, int i) {
        processField(type.getSimpleName(), type.getSimpleName(), i);
	}
	
	private void buildEntryType(String name, Class innerType, Class innerType2) {
	
		typeMap.put(currentClass(), getPath());
		
        builder.append(getTabs())
                .append(MESSAGE)
                .append(SPACE)
                .append(name)
                .append(OPEN_BLOCK)
                .append(NEWLINE);
		
		tabDepth++;
		
		if(!typeMap.containsKey(innerType)){
            fieldStack.push(innerType);
			typeMap.remove(innerType);
            typeMap.put(
                    innerType,
                    getPath() + PATH_SEPERATOR + name + PATH_SEPERATOR + innerType.getSimpleName());
		}
        processField(typeMap.get(innerType).substring(typeMap.get(innerType).lastIndexOf(".") + 1).trim(), "key", 1);
		
		if(!typeMap.containsKey(innerType2)){
            fieldStack.push(innerType2);
			typeMap.remove(innerType2);
            typeMap.put(
                    innerType2,
                    getPath()
                            + PATH_SEPERATOR
                            + name
                            + PATH_SEPERATOR
                            + innerType2.getSimpleName());
		}
        processField(typeMap.get(innerType2).substring(typeMap.get(innerType2).lastIndexOf(".") + 1).trim(), "value", 2);
		
		tabDepth--;
		
		builder.append(getTabs()).append(CLOSE_BLOCK).append(NEWLINE);
	}

	private void processEnum(Class enumType){
		
		classStack.push(enumType);
		typeMap.put(enumType, getPath());
		classStack.pop();
		
        builder.append(getTabs())
                .append(ENUM)
                .append(SPACE)
                .append(enumType.getSimpleName())
                .append(OPEN_BLOCK)
                .append(NEWLINE);
		
		tabDepth++;
		
		int i = 0;
		for(Object e : enumType.getEnumConstants()){
            builder.append(getTabs())
                    .append(e.toString())
                    .append(" = ")
                    .append(i)
                    .append(LINE_END)
                    .append(NEWLINE);
		}
		
		tabDepth--;
		
		builder.append(getTabs()).append(CLOSE_BLOCK).append(NEWLINE);
	}
    private void addHeader() {
        builder.append(COMMENT)
                .append(SPACE)
                .append("Generated by ")
                .append(NAME)
                .append(SPACE)
                .append(VERSION)
                .append(" on ")
                .append(new Date())
                .append(NEWLINE);
    }

    private void addSyntax() {
        builder.append(SYNTAX).append("=").append(PROTO).append(";").append(NEWLINE);
        builder.append(NEWLINE);
    }

    private void addPackage() {
        builder.append(OPTION)
                .append(SPACE)
                .append(JAVA_PACKAGE)
                .append("=")
                .append("\"" + currentClass().getName() + "Proto\"")
                .append(LINE_END)
                .append(NEWLINE);
        builder.append(OPTION)
                .append(SPACE)
                .append(OUTER_CLASS)
                .append("=")
                .append("\"" + currentClass().getSimpleName() + "Proto\"")
                .append(LINE_END)
                .append(NEWLINE);
        builder.append(OPTION)
                .append(SPACE)
                .append(JAVA_MULTIPLE_FILES)
                .append("=")
                .append(TRUE)
                .append(LINE_END)
                .append(NEWLINE);
        builder.append(NEWLINE);
        builder.append(PACKAGE)
                .append(SPACE)
                .append(currentClass().getSimpleName() + "Proto")
                .append(LINE_END)
                .append(NEWLINE);
        builder.append(NEWLINE);
    }

    // Function to build the proto file from the Java class.
    private void generateProtoFile(){
        builder = new StringBuilder();
        addHeader();
        addSyntax();
        addPackage();

        buildMessage();
        classStack.push(origClass);
        buildClassMethod();
    }
	@Override
	/**
	 * If the Proto file has not been generated, generate it. Then return it in string format.
	 * @return String - a String representing the proto file representing this class.
	 */
    public String toString() {
		if(builder == null){
			generateProtoFile();
		}
		return builder.toString();
	}

}
