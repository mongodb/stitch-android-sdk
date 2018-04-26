package com.mongodb.stitch

import android.support.test.runner.AndroidJUnit4
import android.util.Log
import com.mongodb.stitch.android.BsonUtils
import org.bson.Document
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

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

    @Test
    fun testBinaryToJson() {
        val data = ByteArray(10)
        data[0] = 42
        data[4] = 42
        data[9] = 42

        val testBytes = org.bson.types.Binary(data)
        val doc = Document()
        doc["data"] = testBytes

        val json = doc.toJson(BsonUtils.EXTENDED_JSON_WRITER_SETTINGS)
        assertThat(
                "{ \"data\" : { \"\$binary\" : { \"base64\" : \"KgAAACoAAAAAKg==\", \"subType\" : \"00\" } } }"
                        .equals(json)
        )
    }
}
