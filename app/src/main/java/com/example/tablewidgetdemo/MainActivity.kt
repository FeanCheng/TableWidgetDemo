package com.example.tablewidgetdemo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val root = findViewById<FrameLayout>(R.id.root_view)

        text_view_add_vertical_table.setOnClickListener {
            root.addView(
                TableWidget(this,isVertical = true).apply {
                mOnTableItemClickListener = object :TableWidget.OnTableItemClickListener{
                    override fun onItemClick(view: TableWidget, entity: TableEntity) {
                        // do something
                        Toast.makeText(context,"click item ${entity.hashCode()}",Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
        text_view_add_horizontal_table.setOnClickListener {
            root.addView(
                TableWidget(this).apply {
                    mOnTableItemClickListener = object :TableWidget.OnTableItemClickListener{
                        override fun onItemClick(view: TableWidget, entity: TableEntity) {
                            // do something
                            Toast.makeText(context,"click item ${entity.hashCode()}",Toast.LENGTH_SHORT).show()
                        }
                    }
                })
        }
    }
}