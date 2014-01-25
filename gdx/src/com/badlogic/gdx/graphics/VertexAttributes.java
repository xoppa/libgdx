/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.badlogic.gdx.graphics;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Array.ArrayIterator;

/** Instances of this class specify the vertex attributes of a mesh. VertexAttributes are used by {@link Mesh} instances to define
 * its vertex structure. Vertex attributes have an order. The order is specified by the order they are added to this class.
 * 
 * @author mzechner */
public final class VertexAttributes implements Iterable<VertexAttribute> {
	/** The usage of a vertex attribute.
	 * 
	 * @author mzechner */
	public static final class Usage {
		public static final int Position = 1;
		public static final int Color = 2;
		public static final int ColorPacked = 4;
		public static final int Normal = 8;
		public static final int TextureCoordinates = 16;
		public static final int Generic = 32;
		public static final int BoneWeight = 64;
		public static final int Tangent = 128;
		public static final int BiNormal = 256;
	}

	/** The data type of each component of a vertex attribute 
	 * @author Xoppa */
	public static enum DataType {
		/** A single signed byte value */
		Byte(GL10.GL_BYTE, 1), 
		/** A single unsigned byte value */
		UnsignedByte(GL10.GL_UNSIGNED_BYTE, 1),
		/** A signed short value (2 bytes) */
		Short(GL10.GL_SHORT, 2),
		/** An unsigned short value (2 bytes) */
		UnsignedShort(GL10.GL_UNSIGNED_SHORT, 2),
		/** A signed integer value (4 bytes), requires at least GL ES 2.0 */
		Int(GL20.GL_INT, 4),
		/** An unsigned integer value (4 bytes), requires at lease GL ES 2.0 */
		UnsignedInt(GL20.GL_UNSIGNED_INT, 4),
		/** A single floating point value (4 bytes) */
		Float(GL10.GL_FLOAT, 4);
		/** The OpenGL enum value used e.g. in the call to {@link GL10#glVertexPointer(int, int, int, java.nio.Buffer)} */
		public final int glEnum;
		/** The size in bytes of a single value of the specified data type. */
		public final int size;
		private DataType(int glEnum, int size) {
			this.glEnum = glEnum;
			this.size = size;
		}
	}
	
	/** the attributes in the order they were specified **/
	private final VertexAttribute[] attributes;

	/** the size of a single vertex in bytes **/
	public final int vertexSize;
	
	/** cache of the value calculated by {@link #getMask()} **/
	private long mask = -1;
	
	private ReadonlyIterable<VertexAttribute> iterable;

	/** Constructor, sets the vertex attributes in a specific order */
	public VertexAttributes (VertexAttribute... attributes) {
		this(true, attributes);
	}
	
	/**
	 * Constructor, sets the vertex attributes in a specific order
	 * @param pedantic True to check the validity of the attributes (for OpenGL ES 1.x). 
	 * @param attributes Each attribute within the vertex (in the specified order).
	 */
	public VertexAttributes (boolean pedantic, VertexAttribute... attributes) {
		if (attributes.length == 0) throw new IllegalArgumentException("attributes must be >= 1");

		VertexAttribute[] list = new VertexAttribute[attributes.length];
		for (int i = 0; i < attributes.length; i++)
			list[i] = attributes[i];

		this.attributes = list;

		if (pedantic)
			checkValidity();
		
		vertexSize = calculateOffsets();
		
		if (vertexSize % 4 != 0)
			throw new GdxRuntimeException("Vertex size must be aligned to four bytes");
	}

	/** Returns the offset for the first VertexAttribute with the specified usage.
	 * @param usage The usage of the VertexAttribute. */
	public int getOffset (int usage) {
		VertexAttribute vertexAttribute = findByUsage(usage);
		if (vertexAttribute == null) return 0;
		return vertexAttribute.offset / 4;
	}

	/** Returns the first VertexAttribute for the given usage.
	 * @param usage The usage of the VertexAttribute to find. */
	public VertexAttribute findByUsage (int usage) {
		int len = size();
		for (int i = 0; i < len; i++)
			if (get(i).usage == usage) return get(i);
		return null;
	}

	private int calculateOffsets () {
		int count = 0;
		for (final VertexAttribute attribute : attributes) {
			attribute.offset = count;
			count += attribute.size;
		}
		return count;
	}

	private void checkValidity () {
		boolean pos = false;
		boolean cols = false;
		boolean nors = false;

		for (int i = 0; i < attributes.length; i++) {
			VertexAttribute attribute = attributes[i];
			if (attribute.usage == Usage.Position) {
				if (pos) throw new IllegalArgumentException("two position attributes were specified");
				pos = true;
			}

			if (attribute.usage == Usage.Normal) {
				if (nors) throw new IllegalArgumentException("two normal attributes were specified");
			}

			if (attribute.usage == Usage.Color || attribute.usage == Usage.ColorPacked) {
				if (attribute.numComponents != 4) throw new IllegalArgumentException("color attribute must have 4 components");

				if (cols) throw new IllegalArgumentException("two color attributes were specified");
				cols = true;
			}
		}

		if (pos == false) throw new IllegalArgumentException("no position attribute was specified");
	}

	/** @return the number of attributes */
	public int size () {
		return attributes.length;
	}

	/** @param index the index
	 * @return the VertexAttribute at the given index */
	public VertexAttribute get (int index) {
		return attributes[index];
	}

	public String toString () {
		StringBuilder builder = new StringBuilder();
		builder.append("[");
		for (int i = 0; i < attributes.length; i++) {
			builder.append("(");
			builder.append(attributes[i].alias);
			builder.append(", ");
			builder.append(attributes[i].usage);
			builder.append(", ");
			builder.append(attributes[i].numComponents);
			builder.append(", ");
			builder.append(attributes[i].offset);
			builder.append(")");
			builder.append("\n");
		}
		builder.append("]");
		return builder.toString();
	}

	@Override
	public boolean equals (final Object obj) {
		if (!(obj instanceof VertexAttributes)) return false;
		VertexAttributes other = (VertexAttributes)obj;
		if (this.attributes.length != other.size()) return false;
		for (int i = 0; i < attributes.length; i++) {
			if (!attributes[i].equals(other.attributes[i])) return false;
		}
		return true;
	}

	/**
	 * Calculates a mask based on the contained {@link VertexAttribute} instances. The mask
	 * is a bit-wise or of each attributes {@link VertexAttribute#usage}.
	 * @return the mask
	 */
	public long getMask () {
		if(mask == -1) {
			long result = 0;
			for(int i = 0; i < attributes.length; i++) {
				result |= attributes[i].usage;
			}
			mask = result;
		}
		return mask;
	}
	
	@Override
	public Iterator<VertexAttribute> iterator () {
		if (iterable == null) iterable = new ReadonlyIterable<VertexAttribute>(attributes);
		return iterable.iterator();
	}
	
	static public class ReadonlyIterator<T> implements Iterator<T>, Iterable<T> {
		private final T[] array;
		int index;
		boolean valid = true;

		public ReadonlyIterator (T[] array) {
			this.array = array;
		}

		@Override
		public boolean hasNext () {
			if (!valid) throw new GdxRuntimeException("#iterator() cannot be used nested.");
			return index < array.length;
		}

		@Override
		public T next () {
			if (index >= array.length) throw new NoSuchElementException(String.valueOf(index));
			if (!valid) throw new GdxRuntimeException("#iterator() cannot be used nested.");
			return array[index++];
		}

		@Override
		public void remove () {
			throw new GdxRuntimeException("Remove not allowed.");
		}

		public void reset () {
			index = 0;
		}

		@Override
		public Iterator<T> iterator () {
			return this;
		}
	}
	
	static public class ReadonlyIterable<T> implements Iterable<T> {
		private final T[] array;
		private ReadonlyIterator iterator1, iterator2;

		public ReadonlyIterable (T[] array) {
			this.array = array;
		}
		
		@Override
		public Iterator<T> iterator () {
			if (iterator1 == null) {
				iterator1 = new ReadonlyIterator(array);
				iterator2 = new ReadonlyIterator(array);
			}
			if (!iterator1.valid) {
				iterator1.index = 0;
				iterator1.valid = true;
				iterator2.valid = false;
				return iterator1;
			}
			iterator2.index = 0;
			iterator2.valid = true;
			iterator1.valid = false;
			return iterator2;
		}
	}
}
