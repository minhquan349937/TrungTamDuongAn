import { useEffect, useState } from "react";
import type { FormEvent, ReactNode } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { Button, Image, Result, Spin } from "antd";
import {
  Clock, Facebook, Image as ImageIcon, MapPin, MessageCircle, Phone, Send, Sprout, UserRound,
} from "lucide-react";
import { useAuth } from "../auth/AuthProvider";
import { normalizePhone, websiteApi } from "../services/websiteApi";
import type { PublicContent } from "../services/websiteApi";
import "./public-website.css";

const scrollToId = (id: string) =>
  document.getElementById(id)?.scrollIntoView({
    behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth",
    block: "start",
  });

/** Plain text only: React escapes everything, blank lines become paragraphs. */
function Paragraphs({ text }: { text: string | null | undefined }) {
  return <>{(text ?? "").split(/\n+/).filter((line) => line.trim()).map((line, index) => <p key={index}>{line}</p>)}</>;
}

function Picture({ src, alt, icon }: { src: string | null; alt: string; icon?: ReactNode }) {
  const [broken, setBroken] = useState(false);
  if (!src || broken)
    return <div className="g-placeholder" role="img" aria-label={alt}>{icon ?? <ImageIcon size={30} />}</div>;
  return <img src={src} alt={alt} loading="lazy" onError={() => setBroken(true)} />;
}

function Heading({ id, eyebrow, title, summary }: { id?: string; eyebrow?: string; title: string; summary?: string | null }) {
  return (
    <div className="g-heading">
      {eyebrow && <span className="g-eyebrow">{eyebrow}</span>}
      <h2 id={id}>{title}</h2>
      {summary && <p>{summary}</p>}
    </div>
  );
}

const emptyForm = { parentName: "", phone: "", childAge: "", message: "", consent: false, website: "" };

function ConsultForm({ preview }: { preview: boolean }) {
  const [values, setValues] = useState(emptyForm);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState("");
  const [failure, setFailure] = useState("");
  const [again, setAgain] = useState(false);
  const set = (field: keyof typeof emptyForm, value: string | boolean) => setValues((old) => ({ ...old, [field]: value }));

  useEffect(() => {
    if (!done) return;
    setAgain(false);
    const timer = window.setTimeout(() => setAgain(true), 30000); // limits back-to-back submissions from the same page
    return () => window.clearTimeout(timer);
  }, [done]);

  function validate() {
    const found: Record<string, string> = {};
    const name = values.parentName.trim();
    if (name.length < 2) found.parentName = "Vui lòng nhập họ tên phụ huynh.";
    else if (name.length > 150) found.parentName = "Họ tên tối đa 150 ký tự.";
    if (!normalizePhone(values.phone.trim())) found.phone = "Số điện thoại chưa hợp lệ. Ví dụ: 0912 345 678 hoặc +84 912 345 678.";
    if (values.childAge.trim().length > 50) found.childAge = "Tối đa 50 ký tự.";
    if (values.message.trim().length > 2000) found.message = "Tối đa 2000 ký tự.";
    if (!values.consent) found.consent = "Vui lòng đồng ý để trung tâm liên hệ tư vấn.";
    return found;
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (submitting || preview) return;
    setFailure("");
    const found = validate();
    setErrors(found);
    if (Object.keys(found).length) return;
    setSubmitting(true);
    try {
      setDone(await websiteApi.submitConsultation({
        ...values, parentName: values.parentName.trim(), phone: values.phone.trim(),
        childAge: values.childAge.trim(), message: values.message.trim(),
      }));
      setValues(emptyForm);
    } catch (error) {
      setFailure((error as Error).message || "Chưa gửi được yêu cầu. Vui lòng thử lại.");
    } finally {
      setSubmitting(false);
    }
  }

  if (done)
    return (
      <div className="g-form g-form-done" role="status">
        <Sprout size={34} />
        <h3>Cảm ơn bạn đã tin tưởng</h3>
        <p>{done}</p>
        <button type="button" className="g-btn g-btn-soft" disabled={!again} onClick={() => setDone("")}>
          {again ? "Gửi thêm yêu cầu" : "Có thể gửi thêm sau 30 giây"}
        </button>
      </div>
    );

  const field = (id: keyof typeof emptyForm, label: string, input: ReactNode, required = false) => (
    <div className="g-field">
      <label htmlFor={`consult-${id}`}>{label}{required && <b aria-hidden="true"> *</b>}</label>
      {input}
      {errors[id] && <small id={`consult-${id}-error`} className="g-error">{errors[id]}</small>}
    </div>
  );
  const aria = (id: string) => ({ id: `consult-${id}`, "aria-invalid": Boolean(errors[id]), "aria-describedby": errors[id] ? `consult-${id}-error` : undefined });

  return (
    <form className="g-form" onSubmit={submit} noValidate>
      {preview && <p className="g-note">Bản xem trước: biểu mẫu không gửi dữ liệu.</p>}
      {field("parentName", "Họ tên phụ huynh", <input {...aria("parentName")} autoComplete="name" maxLength={150}
        value={values.parentName} onChange={(e) => set("parentName", e.target.value)} />, true)}
      {field("phone", "Số điện thoại", <input {...aria("phone")} type="tel" autoComplete="tel" inputMode="tel" maxLength={20}
        value={values.phone} onChange={(e) => set("phone", e.target.value)} />, true)}
      {field("childAge", "Độ tuổi của trẻ", <input {...aria("childAge")} maxLength={50} placeholder="Ví dụ: 3 tuổi 6 tháng"
        value={values.childAge} onChange={(e) => set("childAge", e.target.value)} />)}
      {field("message", "Nội dung cần tư vấn", <textarea {...aria("message")} rows={4} maxLength={2000}
        value={values.message} onChange={(e) => set("message", e.target.value)} />)}
      <div className="g-trap" aria-hidden="true">
        <label htmlFor="consult-website">Để trống</label>
        <input id="consult-website" tabIndex={-1} autoComplete="off" value={values.website} onChange={(e) => set("website", e.target.value)} />
      </div>
      <div className="g-field">
        <label className="g-check" htmlFor="consult-consent">
          <input id="consult-consent" type="checkbox" checked={values.consent} aria-invalid={Boolean(errors.consent)}
            onChange={(e) => set("consent", e.target.checked)} />
          <span>Tôi đồng ý để trung tâm liên hệ tư vấn qua số điện thoại đã cung cấp.</span>
        </label>
        {errors.consent && <small className="g-error">{errors.consent}</small>}
      </div>
      {failure && <div className="g-alert" role="alert">{failure}</div>}
      <button type="submit" className="g-btn g-btn-primary" disabled={submitting || preview}>
        <Send size={17} /> {submitting ? "Đang gửi…" : "Gửi yêu cầu tư vấn"}
      </button>
    </form>
  );
}

const categoryLabel = { SPACE: "Không gian", ACTIVITY: "Hoạt động" } as const;

export function PublicWebsiteView({ content, preview = false, onClose }: { content: PublicContent; preview?: boolean; onClose?: () => void }) {
  const { settings, sections, albums } = content;
  const name = settings?.centerName || "Trung tâm";
  const items = (key: string) => content.items.filter((item) => item.sectionKey === key);
  const [openService, setOpenService] = useState<string | null>(null);
  const [category, setCategory] = useState<"ALL" | "SPACE" | "ACTIVITY">("ALL");
  const hero = sections.HERO, story = sections.STORY;
  const show = {
    audience: Boolean(sections.AUDIENCE) && (items("AUDIENCE").length > 0 || Boolean(sections.AUDIENCE.body)),
    services: Boolean(sections.SERVICES) && content.services.length > 0,
    team: (Boolean(sections.TEAM) && content.team.length > 0) || (Boolean(sections.PROCESS) && items("PROCESS").length > 0),
    progress: Boolean(sections.PROGRESS) && (items("PROGRESS").length > 0 || Boolean(sections.PROGRESS.body)),
    gallery: Boolean(sections.GALLERY) && albums.length > 0,
    faq: Boolean(sections.FAQ) && content.faqs.length > 0,
  };
  const nav = ([
    ["about", "Giới thiệu", true], ["audience", "Đối tượng", show.audience], ["services", "Dịch vụ", show.services],
    ["team", "Đội ngũ", show.team], ["progress", "Tiến bộ", show.progress], ["gallery", "Không gian", show.gallery],
    ["faq", "Hỏi đáp", show.faq], ["contact", "Liên hệ", true],
  ] as [string, string, boolean][]).filter((entry) => entry[2]);
  const hasBothCategories = new Set(albums.map((album) => album.category)).size > 1;
  const shownAlbums = albums.filter((album) => category === "ALL" || album.category === category);
  const cta = hero?.buttonLabel || "Đăng ký tư vấn";

  useEffect(() => {
    const previous = document.title;
    document.title = `${name} | Giới thiệu`;
    return () => { document.title = previous; };
  }, [name]);

  return (
    <div className="garden">
      {preview && (
        <div className="g-preview-bar" role="status">
          <span>Bản xem trước bản nháp — chỉ quản trị viên nhìn thấy, khách chưa thấy nội dung này.</span>
          {onClose && <button type="button" onClick={onClose}>Đóng xem trước</button>}
        </div>
      )}
      <header className="g-header">
        <div className="g-wrap g-header-row">
          <button type="button" className="g-logo" onClick={() => scrollToId("about")} aria-label={name}>
            {settings?.logoUrl ? <img src={settings.logoUrl} alt="" /> : <span className="g-logo-mark"><Sprout size={22} /></span>}
            <span>{name}</span>
          </button>
          <nav className="g-nav" aria-label="Các phần của trang">
            {nav.map(([id, label]) => <button key={id} type="button" onClick={() => scrollToId(id)}>{label}</button>)}
          </nav>
          <button type="button" className="g-btn g-btn-primary g-header-cta" onClick={() => scrollToId("consult")}>{cta}</button>
        </div>
      </header>

      <main>
        <section id="about" className="g-section g-hero" aria-labelledby="hero-title">
          <div className="g-wrap g-hero-grid">
            <div className="g-hero-copy">
              <span className="g-eyebrow">{name}</span>
              <h1 id="hero-title">{hero?.title || name}</h1>
              {hero?.summary && <p className="g-lead">{hero.summary}</p>}
              <button type="button" className="g-btn g-btn-primary g-btn-lg" onClick={() => scrollToId("consult")}>{cta}</button>
            </div>
            <div className="g-hero-art">
              {hero?.imageUrl ? <Picture src={hero.imageUrl} alt={name} /> : (
                <div className="g-garden-art" aria-hidden="true"><i /><i /><i /><Sprout size={72} /></div>
              )}
            </div>
          </div>
          {(story?.title || story?.body || items("STORY").length > 0) && (
            <div className="g-wrap g-story">
              <div className="g-story-copy">
                {story?.title && <h2>{story.title}</h2>}
                {story?.summary && <p className="g-lead">{story.summary}</p>}
                <Paragraphs text={story?.body} />
              </div>
              {story?.imageUrl && <div className="g-story-img"><Picture src={story.imageUrl} alt={story.title ?? name} /></div>}
              {items("STORY").length > 0 && (
                <ul className="g-values" aria-label="Giá trị cốt lõi">
                  {items("STORY").map((item, index) => (
                    <li key={index}><b>{item.title}</b>{item.description && <span>{item.description}</span>}</li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </section>

        {show.audience && (
          <section id="audience" className="g-section g-tint" aria-labelledby="audience-title">
            <div className="g-wrap">
              <Heading id="audience-title" eyebrow="Đối tượng hỗ trợ" title={sections.AUDIENCE.title || "Đối tượng hỗ trợ"} summary={sections.AUDIENCE.summary} />
              {sections.AUDIENCE.body && <div className="g-prose"><Paragraphs text={sections.AUDIENCE.body} /></div>}
              <div className="g-grid g-grid-3">
                {items("AUDIENCE").map((item, index) => (
                  <article key={index} className="g-card">
                    {item.label && <span className="g-tag">{item.label}</span>}
                    <h3>{item.title}</h3>
                    {item.description && <p>{item.description}</p>}
                  </article>
                ))}
              </div>
            </div>
          </section>
        )}

        {show.services && (
          <section id="services" className="g-section" aria-labelledby="services-title">
            <div className="g-wrap">
              <Heading id="services-title" eyebrow="Chương trình" title={sections.SERVICES.title || "Chương trình và dịch vụ"} summary={sections.SERVICES.summary} />
              <div className="g-grid g-grid-3">
                {content.services.map((service) => {
                  const open = openService === service.id;
                  return (
                    <article key={service.id} className="g-card g-service">
                      <div className="g-card-img"><Picture src={service.imageUrl} alt={service.title} /></div>
                      <h3>{service.title}</h3>
                      {service.summary && <p>{service.summary}</p>}
                      {open && (
                        <div className="g-service-more" id={`service-${service.id}`}>
                          <Paragraphs text={service.details} />
                          {service.audience && <p><b>Đối tượng phù hợp: </b>{service.audience}</p>}
                          {service.format && <p><b>Hình thức: </b>{service.format}</p>}
                        </div>
                      )}
                      {(service.details || service.audience || service.format) && (
                        <button type="button" className="g-link" aria-expanded={open} aria-controls={`service-${service.id}`}
                          onClick={() => setOpenService(open ? null : service.id)}>
                          {open ? "Thu gọn" : "Xem chi tiết"}
                        </button>
                      )}
                    </article>
                  );
                })}
              </div>
            </div>
          </section>
        )}

        {show.team && (
          <section id="team" className="g-section g-tint" aria-labelledby="team-title">
            <div className="g-wrap">
              {sections.TEAM && content.team.length > 0 && (
                <>
                  <Heading id="team-title" eyebrow="Đội ngũ chuyên môn" title={sections.TEAM.title || "Đội ngũ chuyên môn"} summary={sections.TEAM.summary} />
                  <div className="g-grid g-grid-3">
                    {content.team.map((member, index) => (
                      <article key={index} className="g-card g-member">
                        <div className="g-avatar"><Picture src={member.photoUrl} alt={member.fullName} icon={<UserRound size={34} />} /></div>
                        <h3>{member.fullName}</h3>
                        {member.jobTitle && <span className="g-tag">{member.jobTitle}</span>}
                        {member.qualifications && <p><b>Bằng cấp: </b>{member.qualifications}</p>}
                        {member.experience && <p><b>Kinh nghiệm: </b>{member.experience}</p>}
                        {member.bio && <p>{member.bio}</p>}
                      </article>
                    ))}
                  </div>
                </>
              )}
              {sections.PROCESS && items("PROCESS").length > 0 && (
                <div className="g-process">
                  <Heading eyebrow="Quy trình đồng hành" title={sections.PROCESS.title || "Quy trình đồng hành"} summary={sections.PROCESS.summary} />
                  <ol className="g-steps">
                    {items("PROCESS").map((step, index) => (
                      <li key={index}>
                        <span className="g-step-no">{index + 1}</span>
                        <div><h3>{step.title}</h3>{step.description && <p>{step.description}</p>}</div>
                      </li>
                    ))}
                  </ol>
                </div>
              )}
            </div>
          </section>
        )}

        {show.progress && (
          <section id="progress" className="g-section" aria-labelledby="progress-title">
            <div className="g-wrap">
              <Heading id="progress-title" eyebrow="Theo dõi tiến bộ" title={sections.PROGRESS.title || "Theo dõi tiến bộ của trẻ"} summary={sections.PROGRESS.summary} />
              {sections.PROGRESS.body && <div className="g-prose"><Paragraphs text={sections.PROGRESS.body} /></div>}
              <div className="g-grid g-grid-3">
                {items("PROGRESS").map((item, index) => (
                  <article key={index} className="g-card g-progress">
                    <span className="g-step-no">{index + 1}</span>
                    <h3>{item.title}</h3>
                    {item.description && <p>{item.description}</p>}
                  </article>
                ))}
              </div>
            </div>
          </section>
        )}

        {show.gallery && (
          <section id="gallery" className="g-section g-tint" aria-labelledby="gallery-title">
            <div className="g-wrap">
              <Heading id="gallery-title" eyebrow="Hình ảnh" title={sections.GALLERY.title || "Không gian và hoạt động"} summary={sections.GALLERY.summary} />
              {hasBothCategories && (
                <div className="g-tabs" role="group" aria-label="Phân loại album">
                  {(["ALL", "SPACE", "ACTIVITY"] as const).map((key) => (
                    <button key={key} type="button" aria-pressed={category === key} className={category === key ? "on" : ""} onClick={() => setCategory(key)}>
                      {key === "ALL" ? "Tất cả" : categoryLabel[key]}
                    </button>
                  ))}
                </div>
              )}
              {shownAlbums.map((album) => (
                <div key={album.id} className="g-album">
                  <h3>{album.title} <span className="g-tag">{categoryLabel[album.category]}</span></h3>
                  {album.description && <p className="g-album-desc">{album.description}</p>}
                  <Image.PreviewGroup>
                    <div className="g-media-grid">
                      {album.media.map((item, index) => (
                        <figure key={index}>
                          {item.type === "video"
                            ? <video controls preload="metadata" src={item.url} aria-label={item.title ?? album.title} />
                            : <Image rootClassName="g-thumb" src={item.url} alt={item.altText || item.title || album.title} preview={{ mask: "Xem ảnh lớn" }} />}
                          {item.title && <figcaption>{item.title}</figcaption>}
                        </figure>
                      ))}
                    </div>
                  </Image.PreviewGroup>
                </div>
              ))}
            </div>
          </section>
        )}

        {show.faq && (
          <section id="faq" className="g-section" aria-labelledby="faq-title">
            <div className="g-wrap g-narrow">
              <Heading id="faq-title" eyebrow="Giải đáp" title={sections.FAQ.title || "Câu hỏi thường gặp"} summary={sections.FAQ.summary} />
              <div className="g-faq">
                {content.faqs.map((faq, index) => (
                  <details key={index}>
                    <summary>{faq.question}</summary>
                    <div><Paragraphs text={faq.answer} /></div>
                  </details>
                ))}
              </div>
            </div>
          </section>
        )}

        <section id="contact" className="g-section g-tint" aria-labelledby="contact-title">
          <div className="g-wrap">
            <Heading id="contact-title" eyebrow="Liên hệ" title={sections.CONTACT?.title || "Liên hệ và đăng ký tư vấn"} summary={sections.CONTACT?.summary} />
            <div className="g-contact">
              <div className="g-info">
                <h3>{name}</h3>
                <ul>
                  {settings?.address && <li><MapPin size={19} /><span>{settings.address}</span></li>}
                  {settings?.hotline && <li><Phone size={19} /><a href={`tel:${settings.hotline.replace(/[^\d+]/g, "")}`}>{settings.hotline}</a></li>}
                  {settings?.workingHours && <li><Clock size={19} /><span>{settings.workingHours}</span></li>}
                  {settings?.zaloUrl && <li><MessageCircle size={19} /><a href={settings.zaloUrl} target="_blank" rel="noopener noreferrer">Nhắn tin qua Zalo</a></li>}
                  {settings?.facebookUrl && <li><Facebook size={19} /><a href={settings.facebookUrl} target="_blank" rel="noopener noreferrer">Trang Facebook</a></li>}
                </ul>
                {settings?.mapEmbedUrl && (
                  <div className="g-map">
                    <iframe title={`Bản đồ ${name}`} src={settings.mapEmbedUrl} loading="lazy" referrerPolicy="no-referrer-when-downgrade" allowFullScreen />
                  </div>
                )}
              </div>
              <div id="consult" className="g-consult">
                <h3>Đăng ký tư vấn</h3>
                <p>Để lại thông tin, trung tâm sẽ liên hệ để trao đổi cùng gia đình.</p>
                <ConsultForm preview={preview} />
              </div>
            </div>
          </div>
        </section>
      </main>

      <footer className="g-footer">
        <div className="g-wrap">© {new Date().getFullYear()} {name}</div>
      </footer>
    </div>
  );
}

function Notice({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="garden g-center">
      <Sprout size={40} />
      <h1>{title}</h1>
      {children}
    </div>
  );
}

/** Public route: published content only, no login. */
export function PublicWebsitePage() {
  const [content, setContent] = useState<PublicContent | null | undefined>(undefined);
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    setContent(undefined);
    setError("");
    websiteApi.published().then((data) => active && setContent(data)).catch((e: Error) => active && setError(e.message));
    return () => { active = false; };
  }, [attempt]);
  if (error)
    return <Notice title="Chưa tải được trang"><p>{error}</p><button className="g-btn g-btn-primary" onClick={() => setAttempt(attempt + 1)}>Thử lại</button></Notice>;
  if (content === undefined) return <div className="garden g-center"><Spin size="large" /></div>;
  if (content === null) return <Notice title="Website đang được cập nhật"><p>Vui lòng quay lại sau ít phút.</p></Notice>;
  return <PublicWebsiteView content={content} />;
}

/** Admin-only route: renders the draft with the same view as visitors will see. */
export function WebsitePreviewPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [content, setContent] = useState<PublicContent | null>(null);
  const [error, setError] = useState("");
  const isAdmin = user?.role === "ADMIN";
  useEffect(() => {
    if (!isAdmin) return;
    websiteApi.preview().then(setContent).catch((e: Error) => setError(e.message));
  }, [isAdmin]);
  if (!user) return <Navigate to="/login" replace />;
  if (!isAdmin)
    return <Result status="403" title="Bạn không có quyền xem bản nháp" extra={<Button href="#/dashboard">Về tổng quan</Button>} />;
  if (error) return <Notice title="Không tải được bản xem trước"><p>{error}</p></Notice>;
  if (!content) return <div className="garden g-center"><Spin size="large" /></div>;
  return <PublicWebsiteView content={content} preview onClose={() => navigate("/website")} />;
}
