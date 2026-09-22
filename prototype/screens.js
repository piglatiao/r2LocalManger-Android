/* =========================================================================
   R2 存储管理器 · Android 原型 — 页面定义
   依赖 app.js 暴露的 ic / sb / appbar / navbar / ROW / th* / GRAD
   ========================================================================= */
window.SCREENS = [

/* ================= 入门 ================= */
{id:'splash',group:'入门',title:'启动检查',note:'冷启动读本地凭证与配置：缺失直接进引导页，完整才后台做 10 秒超时的探测，失败不阻塞进入应用。',
 html: sb() + appbar({title:'R2 存储管理器'}) +
 '<div class="body"><div class="center-col">' +
 '<div class="logo-lg">R2</div>' +
 '<h2 style="margin:20px 0 7px;font-size:18px;letter-spacing:-.02em">正在检查配置与连接</h2>' +
 '<p style="margin:0;font-size:12.5px;color:var(--ink-2);line-height:1.75">读取本地凭证 · 校验 R2 端点 · 探测存储桶<br>预计 1 秒内完成</p>' +
 '<div class="progress brand" style="width:184px;margin-top:22px"><i style="width:62%"></i></div>' +
 '</div></div>'},

{id:'startup-fail',group:'入门',title:'启动检查失败',note:'三类失败各有独立文案与动作：未配置凭证→去配置；配置无效→检查参数；连接失败→重试或去配置。不阻塞进入应用。',
 html: sb() + appbar({title:'R2 存储管理器'}) +
 '<div class="body">' +
 '<div class="banner warn">' + ic('info', 18) + '<span>启动检查未通过：无法连接到 R2，请检查网络或配置。</span></div>' +
 '<div class="card" style="margin:14px 16px"><h4>已读取到的配置</h4>' +
 '<p>Endpoint：https://12b1178…cee5.r2.cloudflarestorage.com<br>区域：auto · 当前桶：picture</p></div>' +
 '</div>' +
 '<div class="scrim" style="background:rgba(11,13,17,.3)"></div>' +
 '<div class="dialog"><h3>无法连接到 R2</h3>' +
 '<p>凭证已填写，但连接探测失败。请确认网络可用，或在设置中核对 Endpoint、桶名与 API Token 权限。</p>' +
 '<div class="acts"><button class="btn btn-ghost" data-goto="home-list">稍后</button>' +
 '<button class="btn btn-primary" data-goto="set-credentials">检查配置</button></div></div>'},

{id:'lock-unlock',group:'入门',title:'应用锁 · 解锁',note:'生物识别优先，失败或不可用时回退 PIN；连续失败 5 次锁定 30 秒。解锁即用密码派生密钥解密本地缓存。',
 html: sb() + appbar({title:'应用已锁定'}) +
 '<div class="body"><div class="center-col" style="justify-content:flex-start;padding-top:34px">' +
 '<div class="empty-ic" style="width:74px;height:74px;border-radius:23px;background:var(--brand-soft);color:var(--brand)">' + ic('lock', 34) + '</div>' +
 '<h2 style="margin:8px 0 5px;font-size:19px;letter-spacing:-.02em">应用已锁定</h2>' +
 '<p style="margin:0;font-size:12.5px;color:var(--ink-2)">请输入密码以继续使用</p>' +
 '<div class="pin-dots"><i class="on"></i><i class="on"></i><i class="on"></i><i></i><i></i><i></i></div>' +
 '<div class="pinpad">' +
 '<button>1</button><button>2</button><button>3</button>' +
 '<button>4</button><button>5</button><button>6</button>' +
 '<button>7</button><button>8</button><button>9</button>' +
 '<button style="font-size:11px;font-weight:700;color:var(--brand)">生物识别</button><button>0</button><button style="font-size:15px">⌫</button>' +
 '</div>' +
 '<button class="btn btn-primary" data-goto="home-list" style="margin-top:20px;padding:0 38px;border-radius:14px">解锁</button>' +
 '<button class="btn btn-text" data-goto="lock-reset" style="margin-top:12px">忘记密码？</button>' +
 '</div></div>'},

{id:'lock-reset',group:'入门',title:'找回密码',note:'用当前账号的 Cloudflare Account ID + API Token 验证身份；校验通过后清空缓存并重建密钥，旧密文一律失效。',
 html: sb() + appbar({back:'lock-unlock',title:'找回密码'}) +
 '<div class="body pad">' +
 '<div class="banner info" style="margin:0 0 18px">' + ic('info', 18) + '<span>验证当前存储桶的 Cloudflare 凭据后即可重设密码。重设后本地缓存会清空并重新生成。</span></div>' +
 '<div class="field"><label>Cloudflare Account ID</label><div class="input"><span class="ph">请输入 Account ID</span></div></div>' +
 '<div class="field"><label>Cloudflare API Token</label><div class="input"><span class="ph">请输入 API Token</span><span class="tail">' + ic('eye', 19) + '</span></div></div>' +
 '<div class="field"><label>新密码</label><div class="input"><span style="letter-spacing:5px;font-size:16px">••••••</span></div><div class="help">至少 4 位字符</div></div>' +
 '<div class="field"><label>确认新密码</label><div class="input"><span style="letter-spacing:5px;font-size:16px">••••••</span></div></div>' +
 '<button class="btn btn-primary full" data-goto="lock-unlock">验证并重设密码</button>' +
 '<button class="btn btn-text full" data-goto="lock-unlock" style="margin-top:12px">返回登录</button>' +
 '</div>'},

{id:'lock-setup',group:'入门',title:'首次设置密码',note:'配置完成后引导设置应用锁；可选择"暂不设置"并记录标记，之后不再重复打扰。',
 html: sb() + appbar({title:'设置应用密码'}) +
 '<div class="body pad">' +
 '<p style="font-size:12.5px;color:var(--ink-2);line-height:1.8;margin:6px 0 20px">配置已完成。设置应用密码后，下次启动需要输入密码才能进入；本地缓存也会用该密码加密。忘记时可用 Cloudflare 凭据找回。</p>' +
 '<div class="field"><label>应用密码</label><div class="input"><span style="letter-spacing:5px;font-size:16px">••••••</span></div></div>' +
 '<div class="field"><label>确认密码</label><div class="input"><span style="letter-spacing:5px;font-size:16px">••••••</span></div></div>' +
 '<div class="card" style="padding:4px 15px"><div class="swrow"><div class="txt"><b>启用生物识别解锁</b><span>优先指纹 / 人脸，失败时回退密码</span></div><div class="switch on"></div></div></div>' +
 '<button class="btn btn-primary full" data-goto="home-list" style="margin-top:6px">设置密码</button>' +
 '<button class="btn btn-text full" data-goto="home-list" style="margin-top:12px">暂不设置</button>' +
 '</div>'},

/* ================= 浏览 ================= */
{id:'home-list',group:'浏览',title:'文件列表（核心页）',note:'AppBar 桶名 Chip 切桶；面包屑与筛选 Chip 跟随滚动收起；长文件名被压缩但大小/时间永远可见；右下 FAB 上传；长按进入多选。',
 html: sb() + appbar({bucket:true,actions:'<button class="iconbtn" data-goto="search-filter">' + ic('search', 21) + '</button><button class="iconbtn">' + ic('more', 21) + '</button>'}) +
 '<div class="crumbar"><button class="crumb cur">picture</button></div>' +
 '<div class="filterbar">' +
 '<button class="chip on">全部类型' + ic('close', 13) + '</button>' +
 '<button class="chip">' + ic('clock', 14) + '修改时间</button>' +
 '<button class="chip" data-goto="search-filter">' + ic('filter', 14) + '筛选</button></div>' +
 '<div class="body">' + FILE_ROWS +
 '<div style="padding:20px;text-align:center;font-size:11.5px;color:var(--ink-3)">已加载全部 6 项 · 触底自动加载下一页</div></div>' +
 '<button class="fab" data-goto="upload-source">' + ic('up', 25) + '</button>' + navbar('files')},

{id:'home-grid',group:'浏览',title:'网格视图',note:'同一 Fragment 只切换 LayoutManager 与 item 布局，横屏 3 列→5 列；长按同样进入多选。',
 html: sb() + appbar({bucket:true,actions:'<button class="iconbtn" data-goto="search-filter">' + ic('search', 21) + '</button><button class="iconbtn">' + ic('list', 21) + '</button>'}) +
 '<div class="crumbar"><button class="crumb">picture</button><span class="crumb-sep">/</span><button class="crumb cur">camera</button></div>' +
 '<div class="body"><div class="grid3">' +
 '<button class="gitem" data-goto="preview-image"><span class="gtile" style="--g:' + GRAD.sunrise + '"></span><span class="gn">image.png</span><span class="gs">1.2 MB</span></button>' +
 '<button class="gitem" data-goto="preview-video"><span class="gtile" style="--g:' + GRAD.ocean + '"><i class="pl"></i><b class="dur">1:24</b></span><span class="gn">video.mp4</span><span class="gs">24.6 MB</span></button>' +
 '<button class="gitem" data-goto="preview-image"><span class="gtile" style="--g:' + GRAD.forest + '"></span><span class="gn">poster.jpg</span><span class="gs">840 KB</span></button>' +
 '<button class="gitem" data-goto="preview-image"><span class="gtile" style="--g:' + GRAD.dusk + '"></span><span class="gn">banner.webp</span><span class="gs">512 KB</span></button>' +
 '<button class="gitem" data-goto="preview-video"><span class="gtile" style="--g:' + GRAD.steel + '"><i class="pl"></i><b class="dur">0:38</b></span><span class="gn">clip-02.mp4</span><span class="gs">8.1 MB</span></button>' +
 '<button class="gitem" data-goto="preview-image"><span class="gtile" style="--g:' + GRAD.rose + '"></span><span class="gn">shot-3.png</span><span class="gs">2.3 MB</span></button>' +
 '</div></div>' +
 '<button class="fab" data-goto="upload-source">' + ic('up', 25) + '</button>' + navbar('files')},

{id:'folder-nav',group:'浏览',title:'子目录下钻',note:'面包屑可点任意层级回跳；系统返回键逐级回退；根目录时"返回上级"置灰。',
 html: sb() + appbar({back:'home-list',title:'camera / 2024-01',sub:'12 个对象 · 共 68.4 MB'}) +
 '<div class="crumbar"><button class="crumb">picture</button><span class="crumb-sep">/</span><button class="crumb" data-goto="home-list">camera</button><span class="crumb-sep">/</span><button class="crumb cur">2024-01</button></div>' +
 '<div class="body">' +
 ROW({thumb:thPhoto('amber'),name:'IMG_0142.png',meta:'<span>1.8 MB</span><i></i><span>2024-01-20 10:02</span>',goto:'preview-image'}) +
 ROW({thumb:thPhoto('mint'),name:'IMG_0143.jpg',meta:'<span>2.4 MB</span><i></i><span>2024-01-20 10:05</span>',goto:'preview-image'}) +
 ROW({thumb:thVideo('dusk','0:52'),name:'VID_0012.mp4',meta:'<span>42.1 MB</span><i></i><span>2024-01-19 18:22</span>',goto:'preview-video'}) +
 '</div>' +
 '<button class="fab" data-goto="upload-source">' + ic('up', 25) + '</button>' + navbar('files')},

{id:'search-filter',group:'浏览',title:'搜索与筛选',note:'搜索为本地过滤已加载结果，不发网络请求；筛选条件组合后在顶部以 Chip 呈现并可一键清除。',
 html: sb() + appbar({back:'home-list'}) +
 '<div style="flex:0 0 auto;padding:9px 14px;background:var(--card);border-bottom:1px solid var(--line)">' +
 '<div class="input focus" style="height:43px">' + ic('search', 19) + '<span style="font-size:13.5px;font-weight:550">log</span><span class="tail">' + ic('close', 18) + '</span></div></div>' +
 '<div class="body">' +
 ROW({thumb:thDoc('TXT','#5A6472'),name:'log-20240115.txt',meta:'<span>86 KB</span><i></i><span>2024-01-15 08:02</span>',goto:'preview-text',trail:''}) +
 ROW({thumb:thDoc('TXT','#5A6472'),name:'log-20240114.txt',meta:'<span>92 KB</span><i></i><span>2024-01-14 08:01</span>',goto:'preview-text',trail:''}) +
 '<div style="padding:18px;text-align:center;font-size:11.5px;color:var(--ink-3)">命中 2 项 · 大小写不敏感</div>' +
 '</div>' +
 '<div class="scrim" style="background:rgba(11,13,17,.3)"></div>' +
 '<div class="sheet"><div class="grabber"></div>' +
 '<div class="st">筛选与排序</div><div class="ss">在当前目录已加载的结果中过滤</div>' +
 '<div style="padding:0 20px">' +
 '<div class="sec-title" style="margin-left:0">文件类型</div>' +
 '<div style="display:flex;flex-wrap:wrap;gap:8px"><button class="chip on">全部</button><button class="chip">图片</button><button class="chip">视频</button><button class="chip">音频</button><button class="chip">文档</button><button class="chip">压缩包</button><button class="chip">代码</button></div>' +
 '<div class="sec-title" style="margin-left:0">修改日期</div>' +
 '<div style="display:flex;gap:9px;align-items:center"><div class="input" style="height:42px;flex:1"><span class="ph" style="font-size:12.5px">开始 2024-01-01</span></div><span style="color:var(--ink-4)">—</span><div class="input" style="height:42px;flex:1"><span class="ph" style="font-size:12.5px">结束 2024-01-31</span></div></div>' +
 '<div class="sec-title" style="margin-left:0">排序</div>' +
 '<div style="display:flex;flex-wrap:wrap;gap:8px"><button class="chip">名称</button><button class="chip on">修改时间 ↓</button><button class="chip">大小</button></div>' +
 '</div>' +
 '<div style="display:flex;gap:10px;padding:16px 20px 0"><button class="btn btn-ghost" style="flex:1">清除</button><button class="btn btn-primary" style="flex:2" data-goto="home-list">应用筛选</button></div>' +
 '</div>'},

{id:'multi-select',group:'浏览',title:'多选模式',note:'长按进入；选中行只换底色不改行高，避免列表跳动。顶部为上下文操作栏，底部导航被批量操作栏替换（用 Barrier 统一约束，两套底栏不各写一份 margin）。',
 html: sb() +
 '<div class="appbar"><button class="iconbtn" data-goto="home-list">' + ic('close') + '</button>' +
 '<div class="ttl">已选 3 项<div class="sub" style="color:var(--brand-ink)">共 29.2 MB</div></div>' +
 '<button class="iconbtn" title="全选">' + ic('check', 21) + '</button><button class="iconbtn">' + ic('more', 21) + '</button></div>' +
 '<div class="body">' +
 '<button class="row sel">' + thPhoto('sunrise') + '<span class="main"><span class="rt">image.png</span><span class="rs"><span>1.2 MB</span><i></i><span>2024-01-15 14:30</span></span></span><span class="check on">' + ic('check', 14) + '</span></button>' +
 '<button class="row sel">' + thVideo('ocean','1:24') + '<span class="main"><span class="rt">video.mp4</span><span class="rs"><span>24.6 MB</span><i></i><span>2024-01-15 09:12</span></span></span><span class="check on">' + ic('check', 14) + '</span></button>' +
 '<button class="row sel">' + thDoc('PDF','#E5484D') + '<span class="main"><span class="rt">report.pdf</span><span class="rs"><span>3.4 MB</span><i></i><span>2024-01-14 20:15</span></span></span><span class="check on">' + ic('check', 14) + '</span></button>' +
 '<button class="row">' + thDoc('TXT','#5A6472') + '<span class="main"><span class="rt">log-20240115.txt</span><span class="rs"><span>86 KB</span><i></i><span>2024-01-15 08:02</span></span></span><span class="check"></span></button>' +
 '<button class="row">' + thMono('EXE','#38414F') + '<span class="main"><span class="rt">setup-1.0.0.exe</span><span class="rs"><span>78.4 MB</span><i></i><span>2024-01-12 11:40</span></span></span><span class="check"></span></button>' +
 '</div>' +
 '<div class="actionbar">' +
 '<button data-goto="download-pick">' + ic('down', 22) + '<span>下载</span></button>' +
 '<button>' + ic('share', 22) + '<span>分享</span></button>' +
 '<button class="danger" data-goto="delete-confirm">' + ic('trash', 22) + '<span>删除</span></button>' +
 '</div>'},

{id:'item-actions',group:'浏览',title:'对象操作面板',note:'长按列表项或点行尾 ⋮ 唤起；按使用频率排序，删除置底并加分组间隔；下滑、点遮罩、返回键均可关闭。',
 html: sb() + appbar({bucket:true,actions:'<button class="iconbtn" data-goto="search-filter">' + ic('search', 21) + '</button><button class="iconbtn">' + ic('more', 21) + '</button>'}) +
 '<div class="crumbar"><button class="crumb cur">picture</button></div>' +
 '<div class="body">' + FILE_ROWS + '</div>' +
 '<div class="scrim"></div>' +
 '<div class="sheet"><div class="grabber"></div>' +
 '<div class="st">image.png</div><div class="ss">1.2 MB · 2024-01-15 14:30 · image/png</div>' +
 '<button class="sheet-item" data-goto="preview-image"><span class="sic">' + ic('eye', 20) + '</span>预览</button>' +
 '<button class="sheet-item" data-goto="download-pick"><span class="sic">' + ic('down', 20) + '</span>下载到本机</button>' +
 '<button class="sheet-item"><span class="sic">' + ic('share', 20) + '</span>分享文件</button>' +
 '<button class="sheet-item" data-goto="copy-url"><span class="sic">' + ic('link', 20) + '</span>复制公开链接</button>' +
 '<div class="sheet-sep"></div>' +
 '<button class="sheet-item danger" data-goto="delete-confirm"><span class="sic">' + ic('trash', 20) + '</span>删除</button>' +
 '</div>'},

{id:'copy-url',group:'浏览',title:'复制链接格式',note:'链接优先级：启用的自定义域名 → r2.dev → S3 端点。格式默认记住上次选择；Android 13+ 系统自带复制确认，应用内不再叠 Toast。',
 html: sb() + appbar({bucket:true,actions:'<button class="iconbtn">' + ic('more', 21) + '</button>'}) +
 '<div class="crumbar"><button class="crumb cur">picture</button></div>' +
 '<div class="body">' + FILE_ROWS + '</div>' +
 '<div class="scrim"></div>' +
 '<div class="sheet"><div class="grabber"></div>' +
 '<div class="st">复制公开链接</div><div class="ss">https://files.example.com/image.png</div>' +
 '<button class="sheet-item"><span class="sic">' + ic('link', 20) + '</span><span>纯 URL</span><span class="go" style="color:var(--brand)">' + ic('check', 19) + '</span></button>' +
 '<button class="sheet-item"><span class="sic">' + ic('copy', 20) + '</span>HTML &lt;img&gt; 标签</button>' +
 '<button class="sheet-item"><span class="sic">' + ic('copy', 20) + '</span>Markdown 图片语法</button>' +
 '<div class="sheet-sep"></div>' +
 '<button class="sheet-item"><span class="sic">' + ic('share', 20) + '</span>通过其他 App 分享链接</button>' +
 '</div>' +
 '<div class="snackbar">' + ic('check', 19) + '<span>已复制：https://files.example.com/image.png</span></div>'},

{id:'bucket-switch',group:'浏览',title:'存储桶切换',note:'列出账号下全部桶（创建时间、位置、存储类型）；切换后需重新同步公开域名，并清空当前列表与缩略图的内存态。',
 html: sb() + appbar({bucket:true,actions:'<button class="iconbtn" data-goto="search-filter">' + ic('search', 21) + '</button><button class="iconbtn">' + ic('more', 21) + '</button>'}) +
 '<div class="crumbar"><button class="crumb cur">picture</button></div>' +
 '<div class="body">' + FILE_ROWS + '</div>' +
 '<div class="scrim"></div>' +
 '<div class="sheet"><div class="grabber"></div>' +
 '<div class="st">切换存储桶</div><div class="ss">来自 Cloudflare 管理 API · 共 3 个</div>' +
 '<button class="sheet-item" data-goto="home-list"><span class="thumb th-folder" style="width:40px;height:40px;border-radius:12px">' + ic('db', 20) + '</span>' +
 '<span style="flex:1;min-width:0">picture<div style="font-size:11.5px;color:var(--ink-3);font-weight:450;margin-top:3px">Standard · 2024-01-02 创建</div></span>' +
 '<span class="go" style="color:var(--brand)">' + ic('check', 20) + '</span></button>' +
 '<button class="sheet-item"><span class="sic">' + ic('db', 20) + '</span>' +
 '<span style="flex:1;min-width:0">assets<div style="font-size:11.5px;color:var(--ink-3);font-weight:450;margin-top:3px">InfrequentAccess · apac</div></span></button>' +
 '<button class="sheet-item"><span class="sic">' + ic('db', 20) + '</span>' +
 '<span style="flex:1;min-width:0">backup<div style="font-size:11.5px;color:var(--ink-3);font-weight:450;margin-top:3px">Standard · enam</div></span></button>' +
 '<div class="sheet-sep"></div>' +
 '<button class="sheet-item" data-goto="set-bucket"><span class="sic" style="background:var(--brand-soft);color:var(--brand)">' + ic('plus', 20) + '</span>新建存储桶</button>' +
 '</div>'},

/* ================= 操作 ================= */
{id:'upload-source',group:'操作',title:'上传来源选择',note:'照片与视频走系统 Photo Picker（无需存储权限）；文档走 SAF 多选；拍照写入 FileProvider 缓存后再上传。目标为当前目录前缀。',
 html: sb() + appbar({bucket:true,actions:'<button class="iconbtn">' + ic('more', 21) + '</button>'}) +
 '<div class="crumbar"><button class="crumb">picture</button><span class="crumb-sep">/</span><button class="crumb" data-goto="folder-nav">camera</button><span class="crumb-sep">/</span><button class="crumb cur">2024-01</button></div>' +
 '<div class="body">' + FILE_ROWS + '</div>' +
 '<div class="scrim"></div>' +
 '<div class="sheet"><div class="grabber"></div>' +
 '<div class="st">上传到 camera/2024-01</div><div class="ss">选择上传来源 · 多选会串行上传</div>' +
 '<button class="sheet-item" data-goto="upload-progress"><span class="sic" style="background:var(--brand-soft);color:var(--brand)">' + ic('image', 20) + '</span>' +
 '<span style="flex:1;min-width:0">照片和视频<div style="font-size:11.5px;color:var(--ink-3);font-weight:450;margin-top:3px">系统选择器，无需存储权限</div></span></button>' +
 '<button class="sheet-item" data-goto="upload-progress"><span class="sic">' + ic('doc', 20) + '</span>' +
 '<span style="flex:1;min-width:0">文档与压缩包<div style="font-size:11.5px;color:var(--ink-3);font-weight:450;margin-top:3px">SAF 多选，可跨目录</div></span></button>' +
 '<button class="sheet-item" data-goto="upload-progress"><span class="sic">' + ic('camera', 20) + '</span>' +
 '<span style="flex:1;min-width:0">拍照上传<div style="font-size:11.5px;color:var(--ink-3);font-weight:450;margin-top:3px">拍完直接上传</div></span></button>' +
 '</div>'},

{id:'upload-progress',group:'操作',title:'上传进度',note:'前台服务（dataSync）+ 通知进度；应用内进度卡片显示文件名、百分比、速度与剩余时间，可取消。切后台 / 锁屏继续传输。',
 html: sb() + appbar({bucket:true,actions:'<button class="iconbtn">' + ic('more', 21) + '</button>'}) +
 '<div class="crumbar"><button class="crumb">picture</button><span class="crumb-sep">/</span><button class="crumb cur">camera</button></div>' +
 '<div class="body" style="padding-bottom:140px">' +
 '<div class="notif"><span class="app">R2</span>' +
 '<div style="flex:1"><b>正在上传 · 第 3/8 个文件</b>' +
 '<span>IMG_0143.jpg · 42% · 2.4 MB/s</span>' +
 '<div class="progress brand" style="margin-top:9px"><i style="width:42%"></i></div></div></div>' +
 '<p style="text-align:center;font-size:11.5px;color:var(--ink-3);margin:2px 18px 14px">通知栏样式预览（Android 14 前台服务通知，可取消）</p>' +
 FILE_ROWS +
 '</div>' +
 '<div class="progcard">' +
 '<div class="l1"><span class="up">' + ic('up', 17) + '</span><span>上传 IMG_0143.jpg</span><span class="tag run" style="margin-left:auto">进行中</span></div>' +
 '<div class="progress brand"><i style="width:42%"></i></div>' +
 '<div class="l2"><span>42% · 10.3 / 24.6 MB</span><span>2.4 MB/s · 剩余 00:18</span></div>' +
 '<div style="display:flex;gap:9px;margin-top:12px">' +
 '<button class="btn btn-ghost" style="flex:1;height:40px;font-size:13px" data-goto="transfers">查看全部任务</button>' +
 '<button class="btn btn-danger" style="height:40px;font-size:13px">取消</button></div>' +
 '</div>' + navbar('files')},

{id:'download-pick',group:'操作',title:'下载到本机',note:'单文件默认写 MediaStore.Downloads；批量走 SAF 目录树并持久化授权、记住上次目录。完成后通知可点击打开。',
 html: sb() + appbar({bucket:true,actions:'<button class="iconbtn">' + ic('more', 21) + '</button>'}) +
 '<div class="crumbar"><button class="crumb cur">picture</button></div>' +
 '<div class="body">' + FILE_ROWS + '</div>' +
 '<div class="scrim"></div>' +
 '<div class="sheet"><div class="grabber"></div>' +
 '<div class="st">下载 report.pdf</div><div class="ss">3.4 MB · 请选择保存位置</div>' +
 '<button class="sheet-item"><span class="sic" style="background:var(--blue-soft);color:var(--blue)">' + ic('down', 20) + '</span>' +
 '<span style="flex:1;min-width:0">系统下载目录<div style="font-size:11.5px;color:var(--ink-3);font-weight:450;margin-top:3px">MediaStore.Downloads · 推荐</div></span>' +
 '<span class="go" style="color:var(--brand)">' + ic('check', 19) + '</span></button>' +
 '<button class="sheet-item"><span class="sic">' + ic('folder', 20) + '</span>' +
 '<span style="flex:1;min-width:0">选择其他目录…<div style="font-size:11.5px;color:var(--ink-3);font-weight:450;margin-top:3px">SAF 授权，批量下载保留相对路径</div></span></button>' +
 '<div class="sheet-sep"></div>' +
 '<div class="swrow" style="padding:6px 20px;border:0"><div class="txt"><b>记住此目录</b><span>下次下载直接使用</span></div><div class="switch on"></div></div>' +
 '<div style="padding:10px 20px 0"><button class="btn btn-primary full" data-goto="transfers">开始下载</button></div>' +
 '</div>'},

{id:'delete-confirm',group:'操作',title:'删除确认',note:'单对象与批量共用同一对话框，明示不可撤销与数量；成功后局部移除并失效该目录及全部上级目录缓存。',
 html: sb() + appbar({bucket:true,actions:'<button class="iconbtn">' + ic('more', 21) + '</button>'}) +
 '<div class="crumbar"><button class="crumb cur">picture</button></div>' +
 '<div class="body">' + FILE_ROWS + '</div>' +
 '<div class="scrim" style="background:rgba(11,13,17,.55)"></div>' +
 '<div class="dialog">' +
 '<div style="width:52px;height:52px;border-radius:16px;background:var(--red-soft);color:var(--red);display:grid;place-items:center;margin-bottom:14px">' + ic('trash', 26) + '</div>' +
 '<h3>删除 3 个对象？</h3>' +
 '<p>image.png · video.mp4 · report.pdf<br><span style="color:var(--red);font-weight:600">此操作无法撤销，删除后公开链接将立即失效。</span></p>' +
 '<div class="acts"><button class="btn btn-ghost" data-goto="home-list">取消</button>' +
 '<button class="btn" style="background:var(--red);color:#fff" data-goto="transfers">删除</button></div></div>'},

{id:'transfers',group:'操作',title:'传输任务中心',note:'移动端新增页：桌面版的模态进度层升级为常驻列表。进行中可取消，失败可重试，完成可打开 / 分享 / 复制链接。',
 html: sb() + appbar({title:'传输任务',glyph:'swap',actions:'<button class="iconbtn">' + ic('more', 21) + '</button>'}) +
 '<div class="tabs"><button class="active">进行中 2</button><button>已完成 12</button><button>失败 1</button></div>' +
 '<div class="body pad">' +
 '<div class="tcard" style="--bar:linear-gradient(180deg,#FFA44C,var(--brand))">' +
 '<div class="hd"><span class="tag run">' + ic('up', 12) + '上传</span><b>IMG_0143.jpg</b><span class="tag run">42%</span></div>' +
 '<div class="progress brand"><i style="width:42%"></i></div>' +
 '<div class="ft"><span>10.3 / 24.6 MB · 2.4 MB/s</span><span>剩余 00:18</span></div>' +
 '<div style="display:flex;gap:9px;margin-top:12px"><button class="btn btn-ghost" style="flex:1;height:38px;font-size:13px">取消</button></div></div>' +
 '<div class="tcard" style="--bar:linear-gradient(180deg,#6D9BFF,var(--blue))">' +
 '<div class="hd"><span class="tag blue">' + ic('down', 12) + '下载</span><b>report.pdf</b><span class="tag run">78%</span></div>' +
 '<div class="progress blue"><i style="width:78%"></i></div>' +
 '<div class="ft"><span>2.6 / 3.4 MB · 1.1 MB/s</span><span>剩余 00:01</span></div></div>' +
 '<div class="sec-title" style="margin-left:3px">失败 1</div>' +
 '<div class="tcard" style="--bar:var(--red);border-color:#F6D5D6">' +
 '<div class="hd"><span class="tag fail">' + ic('up', 12) + '上传</span><b>setup-1.0.0.exe</b><span class="tag fail">失败</span></div>' +
 '<p style="margin:0;font-size:12.5px;color:var(--ink-2);line-height:1.6">网络连接失败，请检查网络设置</p>' +
 '<div style="display:flex;gap:9px;margin-top:13px"><button class="btn btn-ghost" style="flex:1;height:38px;font-size:13px">重试</button><button class="btn btn-ghost" style="flex:1;height:38px;font-size:13px">移除任务</button></div></div>' +
 '<div class="sec-title" style="margin-left:3px">已完成</div>' +
 '<div class="tcard" style="--bar:var(--green)">' +
 '<div class="hd"><span class="tag ok">' + ic('up', 12) + '上传</span><b>poster.jpg</b><span class="tag ok">完成</span></div>' +
 '<div style="display:flex;gap:18px;font-size:12.5px;color:var(--ink-2);font-weight:600"><span>打开</span><span>分享</span><span>复制链接</span></div></div>' +
 '</div>' + navbar('transfer')},

/* ================= 预览 ================= */
{id:'preview-image',group:'预览',title:'预览 · 图片',note:'深色沉浸背景，工具条 3 秒无操作淡出；支持双指缩放、双击放大、下拉关闭；转场用共享元素。',
 html: sb(true) +
 '<div class="appbar dark"><button class="iconbtn" data-goto="home-list">' + ic('back') + '</button>' +
 '<div class="ttl">image.png<div class="sub">1.2 MB · 1920 × 1080 · image/png</div></div>' +
 '<button class="iconbtn">' + ic('down', 21) + '</button><button class="iconbtn">' + ic('share', 21) + '</button><button class="iconbtn">' + ic('more', 21) + '</button></div>' +
 '<div class="body"><div class="pv-stage"><div class="pv-hero"></div></div></div>' +
 '<div style="position:absolute;left:0;right:0;bottom:0;padding:16px 20px;background:linear-gradient(transparent,rgba(0,0,0,.72));color:#fff;font-size:11.5px;display:flex;justify-content:space-between">' +
 '<span>100% · 双指缩放</span><span style="opacity:.7">下拉关闭</span></div>'},

{id:'preview-video',group:'预览',title:'预览 · 视频',note:'Media3 ExoPlayer 播放；横屏进入全屏沉浸，支持倍速与进度拖动。',
 html: sb(true) +
 '<div class="appbar dark"><button class="iconbtn" data-goto="home-list">' + ic('back') + '</button>' +
 '<div class="ttl">video.mp4<div class="sub">24.6 MB · H.264 1080p</div></div>' +
 '<button class="iconbtn">' + ic('down', 21) + '</button><button class="iconbtn">' + ic('more', 21) + '</button></div>' +
 '<div class="body"><div class="pv-stage" style="background:#07080B"><div class="pv-video">' + ic('play', 42) + '</div></div></div>' +
 '<div style="position:absolute;left:0;right:0;bottom:0;padding:14px 18px;background:linear-gradient(transparent,rgba(0,0,0,.82));color:#fff">' +
 '<div class="progress" style="background:rgba(255,255,255,.22)"><i style="width:34%;background:linear-gradient(90deg,#FFA44C,var(--brand))"></i></div>' +
 '<div style="display:flex;justify-content:space-between;font-size:11.5px;margin-top:9px;font-variant-numeric:tabular-nums"><span>00:42 / 02:06</span><span style="opacity:.75">1.0× · 全屏</span></div></div>'},

{id:'preview-text',group:'预览',title:'预览 · 文本 / 代码',note:'等宽字体、可选中复制；超过 2 MB 先确认再加载，避免 OOM。',
 html: sb(true) +
 '<div class="appbar dark"><button class="iconbtn" data-goto="home-list">' + ic('back') + '</button>' +
 '<div class="ttl">log-20240115.txt<div class="sub">86 KB · text/plain</div></div>' +
 '<button class="iconbtn">' + ic('copy', 21) + '</button><button class="iconbtn">' + ic('share', 21) + '</button></div>' +
 '<div class="body"><div class="pv-text">' +
 '<span class="ln">1</span><span class="k">2024-01-15 08:02:11</span> [<span class="s">INFO</span>] app.start boot=1420ms\n' +
 '<span class="ln">2</span><span class="k">2024-01-15 08:02:12</span> [<span class="s">INFO</span>] storage.service ready bucket=picture\n' +
 '<span class="ln">3</span><span class="k">2024-01-15 08:02:14</span> [<span class="s">INFO</span>] cache.load entries=1842 size=96.4MB\n' +
 '<span class="ln">4</span><span class="k">2024-01-15 08:03:02</span> [<span class="w">WARN</span>] upload.retry attempt=2\n' +
 '<span class="ln">5</span><span class="k">2024-01-15 08:03:09</span> [<span class="s">INFO</span>] upload.ok bytes=2415823\n' +
 '<span class="ln">6</span><span class="k">2024-01-15 08:07:41</span> [<span class="s">INFO</span>] list.prefix objects=12\n' +
 '<span class="ln">7</span><span class="k">2024-01-15 08:09:33</span> [<span class="s">INFO</span>] url.primary files.example.com' +
 '</div></div>'},

{id:'preview-pdf',group:'预览',title:'预览 · PDF',note:'PdfRenderer 逐页渲染进竖向 RecyclerView，页间距 8dp；超过 25 MB 走"下载后查看"。',
 html: sb(true) +
 '<div class="appbar dark"><button class="iconbtn" data-goto="home-list">' + ic('back') + '</button>' +
 '<div class="ttl">report.pdf<div class="sub">3.4 MB · 第 1 / 12 页</div></div>' +
 '<button class="iconbtn">' + ic('down', 21) + '</button><button class="iconbtn">' + ic('share', 21) + '</button></div>' +
 '<div class="body" style="background:#15171C">' +
 '<div class="pv-page">' +
 '<div style="font-size:15px;font-weight:700;letter-spacing:-.02em;margin-bottom:16px">月度存储用量报告</div>' +
 '<div class="ph-line" style="width:100%"></div><div class="ph-line" style="width:92%"></div><div class="ph-line" style="width:76%"></div>' +
 '<div style="height:16px"></div>' +
 '<div style="height:64px;border-radius:8px;background:linear-gradient(140deg,#FFE0BD,#FFC98B);margin-bottom:16px"></div>' +
 '<div class="ph-line" style="width:100%"></div><div class="ph-line" style="width:86%"></div><div class="ph-line" style="width:62%"></div>' +
 '</div>' +
 '<div style="text-align:center;color:rgba(255,255,255,.42);font-size:11.5px;padding-bottom:20px">第 1 页 · 共 12 页</div>' +
 '</div>'},

{id:'preview-other',group:'预览',title:'预览 · 不支持的类型',note:'不可预览类型展示信息卡 + "下载后打开"，用系统 ACTION_VIEW 交给外部 App。',
 html: sb(true) +
 '<div class="appbar dark"><button class="iconbtn" data-goto="home-list">' + ic('back') + '</button>' +
 '<div class="ttl">setup-1.0.0.exe</div>' +
 '<button class="iconbtn">' + ic('down', 21) + '</button><button class="iconbtn">' + ic('share', 21) + '</button></div>' +
 '<div class="body pad">' +
 '<div class="center-col" style="justify-content:flex-start;min-height:auto;padding-top:36px">' +
 '<div class="thumb th-mono" style="--tone:#38414F;width:80px;height:80px;border-radius:22px;box-shadow:0 14px 30px rgba(0,0,0,.4)"><span style="font-size:15px">EXE</span></div>' +
 '<h3 style="margin:18px 0 5px;font-size:17px;letter-spacing:-.02em">setup-1.0.0.exe</h3>' +
 '<p style="margin:0;font-size:12.5px;color:rgba(255,255,255,.58);line-height:1.7">不支持预览此文件类型，请下载后查看</p>' +
 '<button class="btn btn-brand" data-goto="download-pick" style="margin-top:20px;padding:0 28px">下载后打开</button></div>' +
 '<div class="card" style="margin-top:26px;background:rgba(255,255,255,.05);border-color:rgba(255,255,255,.09);box-shadow:none">' +
 '<h4 style="color:#fff">文件信息</h4>' +
 '<p style="color:rgba(255,255,255,.58)">大小 78.4 MB<br>类型 application/octet-stream<br>修改时间 2024-01-12 11:40<br>公开链接 https://files.example.com/setup-1.0.0.exe</p></div>' +
 '</div>'},

/* ================= 设置 ================= */
{id:'set-home',group:'设置',title:'设置首页',note:'分组列表替代桌面版的独立设置窗口（4 个 Tab）；凭证、桶、缓存、安全、高级各自独立二级页。',
 html: sb() + appbar({title:'设置',glyph:'sliders'}) +
 '<div class="body">' +
 '<div class="sec-title" style="margin-left:19px">账号与凭证</div>' +
 ROW({thumb:thIcon('key','var(--brand-soft)','var(--brand-2)'),name:'R2 凭证配置',meta:'<span>Account ID · API Token · Access Key</span>',goto:'set-credentials',trail:ic('chev',17)}) +
 ROW({thumb:'<span class="thumb th-folder">' + ic('folderFill', 22) + '</span>',name:'已登录账号',meta:'<span>12b1178…cee5 · picture</span>',goto:'set-credentials',trail:ic('chev',17)}) +
 '<div class="sec-title" style="margin-left:19px">存储</div>' +
 ROW({thumb:thIcon('db','var(--blue-soft)','var(--blue)'),name:'存储桶设置',meta:'<span>当前：picture · 共 3 个</span>',goto:'set-bucket',trail:ic('chev',17)}) +
 ROW({thumb:thIcon('cloud','#F1F3F6','var(--ink-2)'),name:'缓存设置',meta:'<span>缩略图 96.4 MB / 512 MB</span>',goto:'set-cache',trail:ic('chev',17)}) +
 '<div class="sec-title" style="margin-left:19px">安全</div>' +
 ROW({thumb:thIcon('shield','var(--green-soft)','var(--green)'),name:'安全与密码',meta:'<span>应用锁已启用 · 生物识别</span>',goto:'set-security',trail:ic('chev',17)}) +
 '<div class="sec-title" style="margin-left:19px">高级</div>' +
 ROW({thumb:thIcon('globe','var(--brand-soft)','var(--brand-2)'),name:'自定义域名与 r2.dev',meta:'<span>files.example.com · 1 个域名</span>',goto:'set-advanced',trail:ic('chev',17)}) +
 ROW({thumb:thIcon('file','#F1F3F6','var(--ink-2)'),name:'运行日志',meta:'<span>最近 200 条 · 可导出分享</span>',goto:'set-home',trail:ic('chev',17)}) +
 '<div style="text-align:center;font-size:11.5px;color:var(--ink-3);padding:12px 0 28px">R2 存储管理器 v1.0.0 (Android)</div>' +
 '</div>' + navbar('settings')},

{id:'set-credentials',group:'设置',title:'凭证配置',note:'Endpoint 由 Account ID 自动生成且只读；密钥默认掩码；未启用应用锁时顶部显示风险横幅。',
 html: sb() + appbar({back:'set-home',title:'R2 凭证配置'}) +
 '<div class="body pad">' +
 '<div class="banner warn" style="margin:0 0 18px">' + ic('info', 18) + '<span>当前未启用应用锁，本机任何人都能查看这些凭证。建议到"安全与密码"启用。</span></div>' +
 '<div class="field"><label>Cloudflare Account ID</label><div class="input mono">12b1178bf0856d6e0b7d787ebd43cee5</div></div>' +
 '<div class="field"><label>Cloudflare API Token</label><div class="input"><span style="letter-spacing:2px">•••••••••••••••••••••</span><span class="tail">' + ic('eye', 19) + '</span></div><div class="help">用于存储桶、自定义域名、r2.dev 等管理操作。</div></div>' +
 '<div class="field"><label>Access Key ID</label><div class="input mono">a1b2c3d4e5f6g7h8<span class="tail">' + ic('eye', 19) + '</span></div></div>' +
 '<div class="field"><label>Secret Access Key</label><div class="input"><span style="letter-spacing:2px">•••••••••••••••••••••••</span><span class="tail">' + ic('eye', 19) + '</span></div></div>' +
 '<div class="field"><label>cf-r2-jurisdiction</label><div class="input">default<span class="tail">' + ic('chev', 17) + '</span></div><div class="help">可选值：default / eu / fedramp</div></div>' +
 '<div class="field"><label>Endpoint（自动生成）</label><div class="input readonly mono" style="font-size:11px">https://12b1178…cee5.r2.cloudflarestorage.com</div></div>' +
 '<div class="field"><label>区域</label><div class="input readonly mono">auto</div></div>' +
 '<div style="display:flex;gap:10px"><button class="btn btn-ghost" style="flex:1">测试连接</button><button class="btn btn-primary" style="flex:1" data-goto="home-list">保存</button></div>' +
 '<div style="text-align:center;margin-top:16px"><button class="btn btn-text" style="color:var(--red)">清除已保存的凭证</button></div>' +
 '</div>'},

{id:'set-bucket',group:'设置',title:'存储桶设置',note:'桶名创建后不可改；删除桶需先清空全部对象，并要求输入桶名二次确认。',
 html: sb() + appbar({back:'set-home',title:'存储桶设置'}) +
 '<div class="body pad">' +
 '<div class="card"><h4>当前存储桶</h4>' +
 '<p style="font-family:var(--mono);color:var(--ink);font-size:13px;font-weight:600">picture</p>' +
 '<p style="margin-top:7px">Standard · location auto · 2024-01-02 创建</p>' +
 '<p style="margin-top:7px">公开地址：https://files.example.com</p></div>' +
 '<div class="sec-title" style="margin-left:4px">全部存储桶（3）</div>' +
 '<div class="card" style="padding:4px 0">' +
 ROW({thumb:thIcon('db','var(--brand-soft)','var(--brand-2)'),name:'picture',meta:'<span>Standard · 当前使用</span>',trail:ic('check',18),goto:'set-bucket'}) +
 ROW({thumb:thIcon('db','#F1F3F6','var(--ink-2)'),name:'assets',meta:'<span>InfrequentAccess · apac</span>',trail:'',goto:'set-bucket'}) +
 ROW({thumb:thIcon('db','#F1F3F6','var(--ink-2)'),name:'backup',meta:'<span>Standard · enam</span>',trail:'',goto:'set-bucket'}) +
 '</div>' +
 '<div class="sec-title" style="margin-left:4px">新建存储桶</div>' +
 '<div class="card"><div class="field"><label>桶名</label><div class="input"><span class="ph">new-bucket-name</span></div><div class="help">3–64 位，小写字母 / 数字 / 中划线，首尾不能是中划线。</div></div>' +
 '<div class="field"><label>位置提示</label><div class="input">auto<span class="tail">' + ic('chev', 17) + '</span></div></div>' +
 '<div class="field" style="margin-bottom:0"><label>存储类型</label><div class="input">Standard<span class="tail">' + ic('chev', 17) + '</span></div></div>' +
 '<button class="btn btn-primary full" style="margin-top:14px">创建桶</button></div>' +
 '<div class="sec-title" style="margin-left:4px">编辑 / 删除当前桶</div>' +
 '<div class="card"><div class="field"><label>存储类型</label><div class="input">Standard<span class="tail">' + ic('chev', 17) + '</span></div></div>' +
 '<div style="display:flex;gap:10px"><button class="btn btn-ghost" style="flex:1">更新存储类型</button><button class="btn btn-danger" style="flex:1">删除桶</button></div>' +
 '<div class="help">删除会先清空桶内所有对象，再执行删除。</div></div>' +
 '</div>'},

{id:'set-cache',group:'设置',title:'缓存设置',note:'缩略图上限与统计沿用桌面版；淘汰策略为 LRU，超限自动清理最久未使用条目。',
 html: sb() + appbar({back:'set-home',title:'缓存设置'}) +
 '<div class="body pad">' +
 '<div class="card" style="padding:4px 15px">' +
 '<div class="swrow"><div class="txt"><b>启用本地缩略图缓存</b><span>图片与视频首帧压缩后落盘（AES-256-GCM 加密）</span></div><div class="switch on"></div></div>' +
 '<div class="swrow"><div class="txt"><b>缓存文件列表</b><span>切换桶 / 目录直接本地秒开</span></div><div class="switch on"></div></div>' +
 '</div>' +
 '<div class="field"><label>缓存大小上限</label><div class="input">512 MB<span class="tail">' + ic('chev', 17) + '</span></div><div class="help">可选项 128 MB / 256 MB / 512 MB / 1 GB / 2 GB / 4 GB。</div></div>' +
 '<div class="card">' +
 '<div style="display:flex;justify-content:space-between;font-size:12.5px;margin-bottom:9px"><span>当前占用</span><b style="font-variant-numeric:tabular-nums">96.4 MB / 512 MB</b></div>' +
 '<div class="progress brand"><i style="width:19%"></i></div>' +
 '<div style="display:flex;justify-content:space-between;font-size:12.5px;margin-top:16px"><span>缩略图条目</span><b style="font-variant-numeric:tabular-nums">1 842</b></div>' +
 '<div style="display:flex;justify-content:space-between;font-size:12.5px;margin-top:11px"><span>已缓存目录</span><b style="font-variant-numeric:tabular-nums">37</b></div>' +
 '<div style="display:flex;justify-content:space-between;font-size:12.5px;margin-top:11px"><span>缓存目录</span><span style="font-family:var(--mono);font-size:11px;color:var(--ink-3)">/data/…/files/thumbs</span></div>' +
 '</div>' +
 '<div style="display:flex;gap:10px"><button class="btn btn-danger" style="flex:1">清理缓存</button><button class="btn btn-ghost" style="flex:1">刷新状态</button></div>' +
 '<div style="margin-top:10px"><button class="btn btn-ghost full">打开缓存目录</button></div>' +
 '</div>'},

{id:'set-security',group:'设置',title:'安全与密码',note:'生物识别优先、PIN 兜底；退到后台超过 5 分钟自动上锁（可配）。凭证与缓存均由 Keystore 派生密钥保护。',
 html: sb() + appbar({back:'set-home',title:'安全与密码'}) +
 '<div class="body pad">' +
 '<div class="card"><h4>应用密码锁</h4>' +
 '<p>启用后启动应用需要输入密码。本地缓存（缩略图、文件列表）会用该密码加密保存，忘记密码时可用当前存储桶的 Cloudflare 凭据找回。</p></div>' +
 '<div class="card" style="padding:4px 15px">' +
 '<div class="swrow"><div class="txt"><b>启用应用密码锁</b><span>冷启动需解锁</span></div><div class="switch on"></div></div>' +
 '<div class="swrow"><div class="txt"><b>优先生物识别</b><span>指纹 / 人脸，失败回退密码</span></div><div class="switch on"></div></div>' +
 '<div class="swrow"><div class="txt"><b>后台自动上锁</b><span>退到后台 5 分钟后再次上锁</span></div><div class="switch on"></div></div>' +
 '</div>' +
 '<div class="field"><label>当前密码</label><div class="input"><span class="ph">关闭密码锁或修改密码时需要填写</span></div></div>' +
 '<div class="field"><label>新密码</label><div class="input"><span class="ph">启用时设置密码 / 修改时填写新密码</span></div></div>' +
 '<div class="field"><label>确认新密码</label><div class="input"><span class="ph">请再次输入新密码</span></div></div>' +
 '<button class="btn btn-ghost full">修改密码</button>' +
 '<div class="sec-title" style="margin-left:4px">危险操作</div>' +
 '<div class="card"><p>清除已保存的凭证后需要重新输入才能使用应用。</p>' +
 '<button class="btn btn-danger full" style="margin-top:12px">清除已保存的凭证</button></div>' +
 '</div>'},

{id:'set-advanced',group:'设置',title:'高级 · 自定义域名与 r2.dev',note:'域名增删改与 r2.dev 开关来自 Cloudflare 管理 API；可用域名（Zone）从账号内自动拉取，无需手填 Zone ID。',
 html: sb() + appbar({back:'set-home',title:'自定义域名与 r2.dev'}) +
 '<div class="body pad">' +
 '<div class="card"><h4>公开访问地址</h4>' +
 '<p style="font-family:var(--mono);font-size:12px;color:var(--ink)">https://files.example.com</p>' +
 '<p style="margin-top:7px">优先级：启用的自定义域名 → r2.dev → S3 端点</p></div>' +
 '<div class="sec-title" style="margin-left:4px">自定义域名（1）</div>' +
 '<div class="card"><div style="display:flex;align-items:center;gap:9px"><b style="font-size:13.5px">files.example.com</b><span class="tag ok" style="margin-left:auto">已启用</span></div>' +
 '<p style="margin-top:9px">Zone：example.com · 最低 TLS 1.2</p>' +
 '<div style="display:flex;gap:10px;margin-top:13px"><button class="btn btn-ghost" style="flex:1;height:38px;font-size:13px">编辑</button><button class="btn btn-danger" style="flex:1;height:38px;font-size:13px">解绑</button></div></div>' +
 '<div class="sec-title" style="margin-left:4px">绑定新域名</div>' +
 '<div class="card"><div class="field"><label>域名</label><div class="input"><span class="ph">files.example.com</span></div></div>' +
 '<div class="field"><label>可用域名（Zone）</label><div class="input">example.com<span class="tail">' + ic('chev', 17) + '</span></div><div class="help">从当前账号的可用域名中选择，自动使用对应 Zone ID。</div></div>' +
 '<div class="field" style="margin-bottom:0"><label>最低 TLS 版本</label><div class="input">1.2<span class="tail">' + ic('chev', 17) + '</span></div></div>' +
 '<button class="btn btn-primary full" style="margin-top:14px">绑定域名</button></div>' +
 '<div class="sec-title" style="margin-left:4px">r2.dev 公开访问</div>' +
 '<div class="card"><div class="swrow" style="border:0;padding-top:0"><div class="txt"><b>启用 r2.dev</b><span>pub-xxxxxxxx.r2.dev</span></div><div class="switch"></div></div>' +
 '<p style="margin-top:5px">仅用于调试与临时分享，生产环境建议使用自定义域名。</p></div>' +
 '</div>'},

/* ================= 状态 ================= */
{id:'state-empty',group:'状态',title:'空目录',note:'空态与"无搜索结果""离线"是三个独立视图，不复用同一个空容器，各自给下一步动作。',
 html: sb() + appbar({bucket:true,actions:'<button class="iconbtn">' + ic('refresh', 21) + '</button><button class="iconbtn">' + ic('more', 21) + '</button>'}) +
 '<div class="crumbar"><button class="crumb">picture</button><span class="crumb-sep">/</span><button class="crumb cur">empty-dir</button></div>' +
 '<div class="body"><div class="center-col">' +
 '<div class="empty-ic" style="width:76px;height:76px;border-radius:24px;color:var(--brand);background:var(--brand-soft)">' + ic('folder', 34) + '</div>' +
 '<h3 style="margin:0 0 7px;font-size:16px;letter-spacing:-.02em">此目录暂无文件</h3>' +
 '<p style="margin:0;font-size:12.5px;color:var(--ink-2);line-height:1.7">上传第一个文件，或用其他 App 分享文件到本应用。</p>' +
 '<button class="btn btn-brand" data-goto="upload-source" style="margin-top:20px;padding:0 26px">上传文件</button>' +
 '</div></div>' +
 '<button class="fab" data-goto="upload-source">' + ic('up', 25) + '</button>' + navbar('files')},

{id:'state-offline',group:'状态',title:'离线 / 弱网',note:'优先渲染本地缓存并明确标注离线，不做阻塞式转圈；恢复网络后可一键同步。',
 html: sb() + appbar({bucket:true,actions:'<button class="iconbtn">' + ic('more', 21) + '</button>'}) +
 '<div class="crumbar"><button class="crumb cur">picture</button></div>' +
 '<div class="banner info">' + ic('wifi', 18) + '<span>当前无网络，以下是本地缓存数据（2 小时前同步）。<b>立即同步</b></span></div>' +
 '<div class="body">' + FILE_ROWS + '</div>' +
 '<button class="fab" data-goto="upload-source">' + ic('up', 25) + '</button>' + navbar('files')},

{id:'state-error',group:'状态',title:'错误页',note:'错误按分类给不同文案与主动作：网络类可重试；认证类直接去配置；对象/桶不存在类先刷新列表。',
 html: sb() + appbar({bucket:true,actions:'<button class="iconbtn">' + ic('more', 21) + '</button>'}) +
 '<div class="crumbar"><button class="crumb cur">picture</button></div>' +
 '<div class="body"><div class="center-col">' +
 '<div class="empty-ic" style="width:76px;height:76px;border-radius:24px;background:var(--red-soft);color:var(--red)">' + ic('info', 34) + '</div>' +
 '<h3 style="margin:0 0 7px;font-size:16px;letter-spacing:-.02em">认证失败，请检查 API 凭证配置</h3>' +
 '<p style="margin:0;font-size:12.5px;color:var(--ink-2);line-height:1.7">请先在"设置 &gt; 凭证配置"中填写 R2 凭证，并在"存储桶设置"中确认连接参数。</p>' +
 '<div style="display:flex;gap:10px;margin-top:20px">' +
 '<button class="btn btn-ghost">重试</button>' +
 '<button class="btn btn-primary" data-goto="set-credentials">去配置</button></div>' +
 '</div></div>' + navbar('files')}
];
