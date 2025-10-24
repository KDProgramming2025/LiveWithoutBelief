package info.lwb.e2e

import android.view.View
import android.webkit.WebView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import org.hamcrest.Matcher
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Step 06: In Reader WebView, verify that below every media element there is a "Question" button
 * with correct styling:
 * - For images, videos, and audio: black background, white text
 * - For YouTube videos (iframes): red background, white text
 *
 * This is a read-only WebView DOM/CSS validation using evaluateJavascript, avoiding navigation.
 */
class Step06_ValidateMediaQuestionButtons {

    fun validate_media_question_buttons_styles() {
        TestLog.mark("Step06: validating media question buttons below media in Reader")

        // Ensure the Reader WebView is on screen
        onView(isAssignableFrom(WebView::class.java)).check(matches(isDisplayed()))

        // Wait for DOM/content to be ready
        waitForDomReady(maxMs = 10_000L)

        // Collect and validate via JS
        val js = mediaButtonsValidationJs()
        val resultStr = evalJsAndGetString(js, timeoutMs = 7_000L)
        require(!resultStr.isNullOrBlank()) { "Step06: empty JS result" }

        val json = JSONObject(resultStr)
        val overallOk = json.optBoolean("overallOk", false)
        val counts = json.optJSONObject("counts") ?: JSONObject()
        val failures = json.optJSONArray("failures") ?: JSONArray()

        TestLog.mark(
            "Step06: media counts images=${counts.optInt("images")}, videos=${counts.optInt("videos")}, " +
                "audios=${counts.optInt("audios")}, youtubes=${counts.optInt("youtubes")}"
        )

        if (!overallOk) {
            val sampleMsgs = buildString {
                val cap = minOf(6, failures.length())
                for (i in 0 until cap) {
                    val f = failures.getJSONObject(i)
                    append("#").append(i + 1).append(" type=").append(f.optString("type"))
                        .append(" idx=").append(f.optInt("index"))
                        .append(" reason=").append(f.optString("reason"))
                        .append("; btnText=").append(f.optString("text"))
                        .append("; bg=").append(f.optString("bg"))
                        .append("; color=").append(f.optString("color"))
                        .append('\n')
                }
            }
            TestLog.mark("Step06: failures detected (showing up to 6)\n$sampleMsgs")
        }

        if (!overallOk) {
            TestAbortState.markAborted()
        }

        check(overallOk) {
            val totalFailures = failures.length()
            val first = failures.optJSONObject(0)
            val firstSummary = if (first != null) {
                val t = first.optString("type")
                val idx = first.optInt("index")
                val reason = first.optString("reason")
                val bg = first.optString("bg")
                val color = first.optString("color")
                val text = first.optString("text")
                "firstFailure={type=$t idx=$idx reason=$reason bg=$bg color=$color text='$text'}"
            } else "firstFailure=<none>"
            val countsSummary = "counts={images=${counts.optInt("images")},videos=${counts.optInt("videos")},audios=${counts.optInt("audios")},youtubes=${counts.optInt("youtubes")}}"
            "Step06: media buttons validation failed: $totalFailures issues; $countsSummary; $firstSummary"
        }
    }

    private fun waitForDomReady(maxMs: Long) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(maxMs)
        var last = ""
        while (System.nanoTime() < deadline) {
            val s = evalJsAndGetString(
                "(function(){return JSON.stringify({ready:document.readyState, h:document.documentElement.scrollHeight||0});})()",
                timeoutMs = 2_000L
            )
            last = s ?: ""
            try {
                val j = JSONObject(last)
                val ready = j.optString("ready")
                val h = j.optInt("h", 0)
                if ((ready == "interactive" || ready == "complete") && h > 0) return
            } catch (_: Throwable) {
                // continue polling
            }
            Thread.sleep(150)
        }
        TestLog.mark("Step06: DOM not ready in time; last=$last")
    }

    private fun evalJsAndGetString(script: String, timeoutMs: Long): String? {
        val out = arrayOfNulls<String>(1)
        val latch = CountDownLatch(1)
        onView(isAssignableFrom(WebView::class.java)).perform(object : ViewAction {
            override fun getDescription(): String = "Evaluate JS in WebView"
            override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)
            override fun perform(uiController: UiController, view: View) {
                val wv = view as WebView
                wv.evaluateJavascript(script) { value ->
                    out[0] = unquoteJsString(value)
                    latch.countDown()
                }
            }
        })
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        return out[0]
    }

    private fun unquoteJsString(v: String?): String? {
        if (v == null) return null
        var s = v
        if (s.length >= 2 && s.first() == '"' && s.last() == '"') {
            s = s.substring(1, s.lastIndex)
        }
        // Minimal unescape for typical JSON-serialized strings from evaluateJavascript
        return s
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    private fun mediaButtonsValidationJs(): String {
        return (
            """
            (function(){
                function hasBtnClass(el){ try{ var cn=(el.className||''); return /(\bbtn\b|\bbutton\b|\bquestion-button\b)/i.test(cn); }catch(e){ return false; } }
                function isButtonLike(el){ if(!el) return false; var t=el.tagName?el.tagName.toLowerCase():''; if(t==='button') return true; var r=(el.getAttribute&&el.getAttribute('role'))||''; if(r==='button') return true; if(t==='a' && (r==='button' || hasBtnClass(el))) return true; if(hasBtnClass(el)) return true; return false; }
                function isBlackish(c){ var m=c.match(/rgba?\(([^)]+)\)/i); if(!m) return false; var p=m[1].split(',').map(function(x){return parseFloat(x.trim());}); var r=p[0]||0,g=p[1]||0,b=p[2]||0; return r<=30&&g<=30&&b<=30; }
                function isWhite(c){ var m=c.match(/rgba?\(([^)]+)\)/i); if(!m) return false; var p=m[1].split(',').map(function(x){return parseFloat(x.trim());}); var r=p[0]||0,g=p[1]||0,b=p[2]||0; return r>=235&&g>=235&&b>=235; }
                function isRedDominant(c){ var m=c.match(/rgba?\(([^)]+)\)/i); if(!m) return false; var p=m[1].split(',').map(function(x){return parseFloat(x.trim());}); var r=p[0]||0,g=p[1]||0,b=p[2]||0; return r>=180&&g<=70&&b<=70; }
                function isYouTubeIframe(ifr){ try{ var src=(ifr.getAttribute('src')||'').toLowerCase(); return src.indexOf('youtube.com')>=0||src.indexOf('youtu.be')>=0||src.indexOf('youtube-nocookie.com')>=0; }catch(e){ return false } }
                function getStyle(el){ return window.getComputedStyle(el); }
                function findQuestionButtonBelow(el){
                    if(!el||!el.getBoundingClientRect) return null;
                    var rect=el.getBoundingClientRect();
                    var parent=el.parentElement; var best=null;
                    if(parent){
                        var direct=parent.querySelectorAll(':scope > button, :scope > [role="button"], :scope > a[role="button"], :scope > a.btn, :scope > a.button, :scope > .question-button');
                        for(var i=0;i<direct.length;i++){ var b=direct[i]; var r=b.getBoundingClientRect(); if(r.top>=rect.bottom-2 && r.top<=rect.bottom+500){ best=b; break; } }
                        if(!best){
                            var sib=el.nextElementSibling; var guard=0;
                            while(sib && guard<6){
                                if(isButtonLike(sib)){ var rb=sib.getBoundingClientRect(); if(rb.top>=rect.bottom-2 && rb.top<=rect.bottom+500){ best=sib; break; } }
                                var nested=sib.querySelector && sib.querySelector('button, [role="button"], a[role="button"], a.btn, a.button, .question-button');
                                if(!best && nested){ var rn=nested.getBoundingClientRect(); if(rn.top>=rect.bottom-2 && rn.top<=rect.bottom+500){ best=nested; break; } }
                                sib=sib.nextElementSibling; guard++;
                            }
                        }
                        // Special-case: YouTube iframe inside .yt-wrap with button in sibling .yt-deeplink of the grandparent (.media__item.youtube)
                        if(!best){
                            try{
                                var pClass=(parent.className||'');
                                if((el.tagName||'').toLowerCase()==='iframe' || /\byt-wrap\b/.test(pClass)){
                                    var gp=parent.parentElement;
                                    if(gp){
                                        var after=parent.nextElementSibling; var guard2=0;
                                        while(after && guard2<6){
                                            if(isButtonLike(after)){
                                                var ra=after.getBoundingClientRect(); if(ra.top>=rect.bottom-2 && ra.top<=rect.bottom+500){ best=after; break; }
                                            }
                                            var nested2=after.querySelector && after.querySelector('button, [role="button"], a[role="button"], a.btn, a.button, .question-button, .yt-open-btn');
                                            if(!best && nested2){ var rn2=nested2.getBoundingClientRect(); if(rn2.top>=rect.bottom-2 && rn2.top<=rect.bottom+500){ best=nested2; break; } }
                                            after=after.nextElementSibling; guard2++;
                                        }
                                    }
                                }
                            }catch(e){}
                        }
                    }
                    return best;
                }
                var images=[].slice.call(document.querySelectorAll('img'));
                var videos=[].slice.call(document.querySelectorAll('video'));
                var audios=[].slice.call(document.querySelectorAll('audio'));
                var iframes=[].slice.call(document.querySelectorAll('iframe'));
                var youtubes=iframes.filter(isYouTubeIframe);
                var failures=[];
                function pushFail(type, idx, btn, reason){ failures.push({type:type,index:idx,reason:reason,bg:btn?getStyle(btn).backgroundColor:'',color:btn?getStyle(btn).color:'',text:btn?(btn.textContent||'').trim():''}); }
                function checkBtnCommon(btn){ if(!btn) return 'missing'; var txt=(btn.textContent||'').trim().toLowerCase(); if(txt.indexOf('question')<0) return 'text-not-question'; return ''; }
                // Images/Videos/Audio require a "Question"-text button below with black bg and white text
                images.forEach(function(el,idx){ var btn=findQuestionButtonBelow(el); var cm=checkBtnCommon(btn); if(cm){ pushFail('image',idx,btn,cm); return; } var st=getStyle(btn); if(!isBlackish(st.backgroundColor)) pushFail('image',idx,btn,'bg-not-black'); if(!isWhite(st.color)) pushFail('image',idx,btn,'text-not-white'); });
                videos.forEach(function(el,idx){ var btn=findQuestionButtonBelow(el); var cm=checkBtnCommon(btn); if(cm){ pushFail('video',idx,btn,cm); return; } var st=getStyle(btn); if(!isBlackish(st.backgroundColor)) pushFail('video',idx,btn,'bg-not-black'); if(!isWhite(st.color)) pushFail('video',idx,btn,'text-not-white'); });
                audios.forEach(function(el,idx){ var btn=findQuestionButtonBelow(el); var cm=checkBtnCommon(btn); if(cm){ pushFail('audio',idx,btn,cm); return; } var st=getStyle(btn); if(!isBlackish(st.backgroundColor)) pushFail('audio',idx,btn,'bg-not-black'); if(!isWhite(st.color)) pushFail('audio',idx,btn,'text-not-white'); });
                // YouTube embeds require a red button with white text below (no specific text requirement)
                youtubes.forEach(function(el,idx){ var btn=findQuestionButtonBelow(el); if(!btn){ pushFail('youtube',idx,btn,'missing'); return; } var st=getStyle(btn); if(!isRedDominant(st.backgroundColor)) pushFail('youtube',idx,btn,'bg-not-red'); if(!isWhite(st.color)) pushFail('youtube',idx,btn,'text-not-white'); });
                return JSON.stringify({overallOk: failures.length===0, counts:{images:images.length,videos:videos.length,audios:audios.length,youtubes:youtubes.length}, failures:failures});
            })()
            """
        )
    }
}
