package com.example.tablewidgetdemo

import android.graphics.RectF

/**
 * created by feancheng on 2020/9/30
 * Desc:
 */
data class TableEntity(
    var region:RectF,
    var text:String,
    var row:Int,
    var column:Int,
    var isFocusRegion:Boolean = false
    ) {



    override fun hashCode(): Int {
        return row * 10 + column*1
    }

    override fun toString(): String {
        return "left = ${region.left} right = ${region.right} top = ${region.top} bottom = ${region.bottom}  text = $text"

    }

    fun reset(){
        text = "0"
        isFocusRegion = false
        column = 0
        row = 0
        region = RectF()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if ( other is TableEntity){
            return text == other.text && row == other.row && column == other.column
                    && region.left == other.region.left
                    && region.top == other.region.top
                    && region.bottom == other.region.bottom
                    && region.right == other.region.right
        }
        return false
    }
}