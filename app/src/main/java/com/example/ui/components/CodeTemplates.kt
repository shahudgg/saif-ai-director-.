package com.example.ui.components

data class ProjectFileData(
    val fileName: String,
    val initialCode: String,
    val language: String
)

object CodeTemplates {
    fun getDefaultFile(language: String, appName: String, packageName: String): ProjectFileData {
        val cleanPkg = packageName.ifBlank { "com.example.myapp" }
        return when (language.lowercase()) {
            "kotlin" -> ProjectFileData(
                fileName = "MainActivity.kt",
                language = "Kotlin",
                initialCode = """
package $cleanPkg

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast

/**
 * SAIF AI Studio - $appName
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        val titleText = findViewById<TextView>(R.id.title_text)
        titleText?.text = "$appName"

        Toast.makeText(this, "$appName Ready", Toast.LENGTH_SHORT).show()
    }
}
                """.trimIndent()
            )
            "python" -> ProjectFileData(
                fileName = "main.py",
                language = "Python",
                initialCode = """
# SAIF AI Studio - $appName

def main():
    print("🚀 Welcome to $appName!")
    print("AI Python environment ready.")

if __name__ == "__main__":
    main()
                """.trimIndent()
            )
            "html", "html / web" -> ProjectFileData(
                fileName = "index.html",
                language = "HTML",
                initialCode = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$appName</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            margin: 0;
            padding: 32px 20px;
            background: #0f172a;
            color: #f8fafc;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            min-height: 80vh;
            text-align: center;
        }
        h1 { color: #f97316; margin-bottom: 8px; font-size: 28px; }
        p { color: #94a3b8; max-width: 420px; line-height: 1.6; font-size: 15px; }
        .card {
            background: #1e293b;
            border: 1px solid #334155;
            border-radius: 16px;
            padding: 24px;
            margin-top: 20px;
            max-width: 400px;
            box-shadow: 0 10px 25px rgba(0,0,0,0.3);
        }
        .btn {
            margin-top: 16px;
            padding: 12px 24px;
            background: #10b981;
            color: white;
            border: none;
            border-radius: 10px;
            font-weight: bold;
            font-size: 14px;
            cursor: pointer;
            transition: transform 0.1s;
        }
        .btn:active { transform: scale(0.96); }
    </style>
</head>
<body>
    <div class="card">
        <h1>🚀 $appName</h1>
        <p>Powered by SAIF AI Studio. Edit this HTML, CSS, and JS code in the editor and tap <b>Run</b> to see updates instantly!</p>
        <button class="btn" onclick="document.getElementById('status').innerText = '🎉 Code is running successfully!'">Test Interactive Action</button>
        <div id="status" style="margin-top: 14px; color: #38bdf8; font-weight: 600;"></div>
    </div>
</body>
</html>
                """.trimIndent()
            )
            "cpp", "c / c++" -> ProjectFileData(
                fileName = "main.cpp",
                language = "C++",
                initialCode = """
// SAIF AI Studio - $appName
#include <iostream>

int main() {
    std::cout << "🚀 Welcome to $appName!" << std::endl;
    std::cout << "Native C++ execution ready." << std::endl;
    return 0;
}
                """.trimIndent()
            )
            "javascript" -> ProjectFileData(
                fileName = "index.js",
                language = "JavaScript",
                initialCode = """
// SAIF AI Studio - $appName
function main() {
    console.log("🚀 Welcome to $appName!");
    console.log("JavaScript execution initialized.");
}

main();
                """.trimIndent()
            )
            "typescript" -> ProjectFileData(
                fileName = "index.ts",
                language = "TypeScript",
                initialCode = """
// SAIF AI Studio - $appName
interface AppConfig {
    name: string;
    version: string;
}

const app: AppConfig = {
    name: "$appName",
    version: "1.0.0"
};

console.log(`🚀 ${'$'}{app.name} v${'$'}{app.version} initialized.`);
                """.trimIndent()
            )
            "flutter", "flutter / dart" -> ProjectFileData(
                fileName = "main.dart",
                language = "Dart",
                initialCode = """
// SAIF AI Studio - $appName
void main() {
    print('🚀 Welcome to $appName!');
    print('Flutter & Dart environment ready.');
}
                """.trimIndent()
            )
            "rust" -> ProjectFileData(
                fileName = "main.rs",
                language = "Rust",
                initialCode = """
// SAIF AI Studio - $appName
fn main() {
    println!("🚀 Welcome to $appName!");
    println!("Safe, high-performance Rust execution ready.");
}
                """.trimIndent()
            )
            "go", "go (golang)" -> ProjectFileData(
                fileName = "main.go",
                language = "Go",
                initialCode = """
// SAIF AI Studio - $appName
package main

import "fmt"

func main() {
    fmt.Println("🚀 Welcome to $appName!")
    fmt.Println("Go microservice ready.")
}
                """.trimIndent()
            )
            else -> ProjectFileData(
                fileName = "MainActivity.java",
                language = "Java",
                initialCode = """
package $cleanPkg;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

/**
 * SAIF AI Studio - $appName
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        TextView titleText = findViewById(R.id.title_text);
        if (titleText != null) {
            titleText.setText("$appName");
        }

        Toast.makeText(this, "$appName Ready", Toast.LENGTH_SHORT).show();
    }
}
                """.trimIndent()
            )
        }
    }
}
