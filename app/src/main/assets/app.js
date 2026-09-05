const $=id=>document.getElementById(id);
const fallback=[['Fajr','الفجر','☾'],['Sunrise','الشروق','🌅'],['Dhuhr','الظهر','☀️'],['Asr','العصر','🌤️'],['Maghrib','المغرب','🌇'],['Isha','العشاء','☾']];
let timings={}; let lastUpdated=0;
function clean(hm){return (hm||'').split(' ')[0]}
function fmt(hm){hm=clean(hm);if(!hm)return'--:--';let [h,m]=hm.split(':').map(Number);if(!Number.isFinite(h)||!Number.isFinite(m))return'--:--';let ap=h>=12?'م':'ص';let h12=h%12||12;return `${String(h12).padStart(2,'0')}:${String(m).padStart(2,'0')} ${ap}`}
function minutes(hm){hm=clean(hm);if(!hm)return 99999;let [h,m]=hm.split(':').map(Number);return (Number.isFinite(h)&&Number.isFinite(m))?h*60+m:99999}
function render(){const order=['Fajr','Sunrise','Dhuhr','Asr','Maghrib','Isha'];$('prayerList').innerHTML=order.map((k,i)=>{const x=fallback[i];return `<div class="prayer" data-id="${k}"><div class="pname">${x[1]} <span>${x[2]}</span></div><div class="ptime">${fmt(timings[k])}</div></div>`}).join('')}
function nowTarget(){const d=new Date(),cur=d.getHours()*60+d.getMinutes();let best=null;for(const k of ['Fajr','Dhuhr','Asr','Maghrib','Isha']){if(timings[k]&&minutes(timings[k])>cur){best=k;break}}if(!best){best='Fajr';d.setDate(d.getDate()+1)}let hm=clean(timings[best]);if(!hm)return [best,new Date(d.setHours(4,30,0,0))];let [h,m]=hm.split(':').map(Number);d.setHours(h,m,0,0);return [best,d]}
function tick(){const [key,target]=nowTarget(),now=new Date();const p=fallback.find(x=>x[0]===key)||fallback[0];$('nextName').textContent=p[1]+' '+p[2];document.querySelectorAll('.prayer').forEach(e=>e.classList.toggle('active',e.dataset.id===key));let n=Math.max(0,Math.floor((target-now)/1000));let h=Math.floor(n/3600),m=Math.floor(n%3600/60),s=n%60;$('countdown').textContent=[h,m,s].map(v=>String(v).padStart(2,'0')).join(':')}
function showData(d){
  timings=d.timings||{}; lastUpdated=d.updated||0; render(); tick();
  const lat=Number(d.lat),lon=Number(d.lon);
  if(lat||lon){$('locationName').textContent='تم تحديد موقعي 📍';$('locationStatus').textContent=`GPS • ${lat.toFixed(4)} , ${lon.toFixed(4)}`}
  else $('locationStatus').textContent='جاري انتظار موقع GPS...';
  if(d.exact===false)$('alarmNotice').classList.remove('hidden'); else $('alarmNotice').classList.add('hidden');
  if(d.error){$('syncStatus').textContent=d.error; $('syncStatus').classList.remove('ok')} else {$('syncStatus').textContent=d.source==='offline'?'المواقيت محسوبة محليًا بدون إنترنت':'المواقيت محدثة من خدمة AlAdhan'; $('syncStatus').classList.add('ok')}
}
function loadNative(){if(!window.Android||!Android.getPrayerData){render();tick();return}try{showData(JSON.parse(Android.getPrayerData()))}catch(e){render();tick()}}
function gps(){if(window.Android&&Android.refresh){$('locationStatus').textContent='جاري تحديد GPS وجلب المواقيت...';Android.refresh()}else if(navigator.geolocation){navigator.geolocation.getCurrentPosition(p=>{$('locationName').textContent='تم تحديد موقعي 📍';$('locationStatus').textContent=`GPS • ${p.coords.latitude.toFixed(4)} , ${p.coords.longitude.toFixed(4)}`},()=>{$('locationStatus').textContent='تعذر تحديد الموقع'})}}
window.nativeResume=loadNative;
$('locationBtn').onclick=gps;$('refreshBtn').onclick=gps;
$('alarmSettings').onclick=()=>{if(window.Android&&Android.openExactAlarmSettings)Android.openExactAlarmSettings()};
$('testAdhan').onclick=()=>{if(window.Android&&Android.testAdhan)Android.testAdhan();else alert('اختبار الأذان متاح داخل التطبيق')};
$('chooseAdhan').onclick=()=>alert('الأذان المدمج: الملف الذي أرسلته، بجودة أصلية.');
$('volume').oninput=e=>{let v=+e.target.value;$('volumeValue').textContent=v+'%';localStorage.volume=v;if(window.Android&&Android.setVolume)Android.setVolume(v)};
if(localStorage.volume){$('volume').value=localStorage.volume;$('volumeValue').textContent=localStorage.volume+'%'}
$('date').textContent=new Date().toLocaleDateString('ar-EG',{weekday:'long',year:'numeric',month:'long',day:'numeric'});
loadNative();setInterval(tick,1000);setInterval(loadNative,3000);
