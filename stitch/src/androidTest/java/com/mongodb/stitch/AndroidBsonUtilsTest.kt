package com.mongodb.stitch

import android.support.test.runner.AndroidJUnit4
import com.mongodb.stitch.android.BsonUtils
import org.bson.Document
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidBsonUtilsTest {
    @Test
    fun testParseIterable() {
        val iterable = BsonUtils.parseIterable(
                """
                [{"a": 1},{"b": 2}, "c", 3]
                """
        )

        iterable.forEachIndexed { index, any ->
            when (index) {
                0 -> {
                    val doc = any as Document
                    assertThat(doc["a"] == 1)
                }
                1 -> {
                    val doc = any as Document
                    assertThat(doc["b"] == 2)
                }
                2 -> assertThat(any == "c")
                3 -> assertThat(any == 3)
            }
        }
    }

    @Test
    fun testParseList() {
        val list = BsonUtils.parseList(
                """
                [{"a": 1},{"b": 2}, "c", 3]
                """
        )

        list.forEachIndexed { index, any ->
            when (index) {
                0 -> {
                    val doc = any as Document
                    assertThat(doc["a"] == 1)
                }
                1 -> {
                    val doc = any as Document
                    assertThat(doc["b"] == 2)
                }
                2 -> assertThat(any == "c")
                3 -> assertThat(any == 3)
            }
        }
    }

    @Test
    fun testParseValue() {
        val a = BsonUtils.parseValue("""{"a": 1}""") as Document
        assertThat(a["a"] == 1)
        val b = BsonUtils.parseValue("""{"b": 2}""") as Document
        assertThat(b["b"] == 2)
        val three = BsonUtils.parseValue("""3""")
        assertThat(three == 3)
        val array = BsonUtils.parseValue("""[1,2,3]""") as ArrayList<*>
        assertThat(array[0] == 1)
        assertThat(array[1] == 2)
        assertThat(array[2] == 3)
    }
}
