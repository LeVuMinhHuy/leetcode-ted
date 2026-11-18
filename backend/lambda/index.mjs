import { SESClient, SendEmailCommand } from "@aws-sdk/client-ses";

const ses = new SESClient({ region: process.env.AWS_REGION ?? "ap-southeast-1" });
const FROM = process.env.SES_SENDER_EMAIL;
const APP_URL = "https://tedcode.vercel.app";

// Lambda handler — triggered by SQS.
// Returns batchItemFailures so SQS only retries the records that actually failed.
export const handler = async (event) => {
  const batchItemFailures = [];

  for (const record of event.Records) {
    try {
      const body = JSON.parse(record.body);
      await route(body);
    } catch (err) {
      console.error(`Failed to process record ${record.messageId}:`, err.message);
      batchItemFailures.push({ itemIdentifier: record.messageId });
    }
  }

  return { batchItemFailures };
};

async function route(body) {
  switch (body.event) {
    case "member_solved":
      await handleMemberSolved(body);
      break;
    case "streak_reminder":
      await handleStreakReminder(body);
      break;
    default:
      console.warn("Unknown event type:", body.event);
  }
}

// ── member_solved ─────────────────────────────────────────────────────────────

async function handleMemberSolved({ solver, problem, recipients }) {
  if (!recipients?.length) return;

  await Promise.all(
    recipients.map((r) =>
      sendEmail({
        to: r.email,
        subject: `🎉 ${solver.displayName} just solved ${problem.title}!`,
        html: memberSolvedHtml({ solver, problem, recipient: r }),
        text: memberSolvedText({ solver, problem }),
      })
    )
  );
}

function memberSolvedHtml({ solver, problem, recipient }) {
  const name = recipient.displayName || recipient.username || "there";
  return `<!DOCTYPE html>
<html lang="en">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="margin:0;padding:0;background:#f4f5f7;font-family:system-ui,-apple-system,sans-serif;color:#111">
  <table width="100%" cellpadding="0" cellspacing="0" style="padding:32px 16px">
    <tr><td align="center">
      <table width="100%" style="max-width:520px;background:#fff;border-radius:10px;overflow:hidden;box-shadow:0 1px 4px rgba(0,0,0,.08)">
        <tr>
          <td style="background:#111;padding:20px 28px">
            <span style="color:#fff;font-size:15px;font-weight:700;letter-spacing:.02em">leetcode-ted</span>
          </td>
        </tr>
        <tr>
          <td style="padding:28px 28px 20px">
            <p style="margin:0 0 16px;font-size:15px">Hi ${escHtml(name)},</p>
            <p style="margin:0 0 20px;font-size:15px">
              <strong>${escHtml(solver.displayName)}</strong> just solved a problem on LeetCode 🎉
            </p>
            <table width="100%" cellpadding="0" cellspacing="0">
              <tr>
                <td style="background:#f8f9fa;border:1px solid #e9ecef;border-radius:8px;padding:18px 20px">
                  <div style="font-size:17px;font-weight:700;margin-bottom:14px">${escHtml(problem.title)}</div>
                  <a href="${escHtml(problem.url)}"
                     style="display:inline-block;background:#111;color:#fff;text-decoration:none;padding:9px 18px;border-radius:6px;font-size:13px;font-weight:600">
                    View Problem →
                  </a>
                </td>
              </tr>
            </table>
            <p style="margin:20px 0 0;font-size:14px;color:#555">Stay sharp and keep grinding!</p>
          </td>
        </tr>
        <tr>
          <td style="padding:16px 28px;border-top:1px solid #f0f0f0">
            <p style="margin:0;font-size:12px;color:#999">
              Sent by <a href="${APP_URL}" style="color:#999">${APP_URL}</a>
            </p>
          </td>
        </tr>
      </table>
    </td></tr>
  </table>
</body>
</html>`;
}

function memberSolvedText({ solver, problem }) {
  return [
    `${solver.displayName} just solved ${problem.title} on LeetCode!`,
    ``,
    `${problem.url}`,
    ``,
    `Stay sharp and keep grinding!`,
    `— leetcode-ted (${APP_URL})`,
  ].join("\n");
}

// ── streak_reminder ───────────────────────────────────────────────────────────

async function handleStreakReminder({ laggards }) {
  if (!laggards?.length) return;

  await Promise.all(
    laggards.map((m) =>
      sendEmail({
        to: m.email,
        subject: streakSubject(m.currentStreak),
        html: streakReminderHtml(m),
        text: streakReminderText(m),
      })
    )
  );
}

function streakSubject(streak) {
  if (!streak || streak === 0) return "⏰ Solve a problem today before midnight!";
  return `⏰ Don't break your ${streak}-day streak!`;
}

function streakReminderHtml({ displayName, username, currentStreak }) {
  const name = displayName || username || "there";
  const streakLine =
    currentStreak > 0
      ? `<p style="margin:0 0 10px;font-size:15px">Your current streak is <strong>${currentStreak} day${currentStreak === 1 ? "" : "s"}</strong>. Solve one problem before midnight to keep it going!</p>`
      : `<p style="margin:0 0 10px;font-size:15px">Start a new streak today — solve one problem before midnight!</p>`;

  return `<!DOCTYPE html>
<html lang="en">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="margin:0;padding:0;background:#f4f5f7;font-family:system-ui,-apple-system,sans-serif;color:#111">
  <table width="100%" cellpadding="0" cellspacing="0" style="padding:32px 16px">
    <tr><td align="center">
      <table width="100%" style="max-width:520px;background:#fff;border-radius:10px;overflow:hidden;box-shadow:0 1px 4px rgba(0,0,0,.08)">
        <tr>
          <td style="background:#111;padding:20px 28px">
            <span style="color:#fff;font-size:15px;font-weight:700;letter-spacing:.02em">leetcode-ted</span>
          </td>
        </tr>
        <tr>
          <td style="padding:28px 28px 20px">
            <p style="margin:0 0 16px;font-size:15px">Hi ${escHtml(name)},</p>
            <table width="100%" cellpadding="0" cellspacing="0">
              <tr>
                <td style="background:#fffbeb;border:1px solid #fde68a;border-radius:8px;padding:18px 20px">
                  <div style="font-size:28px;margin-bottom:10px">⏰</div>
                  ${streakLine}
                  <a href="https://leetcode.com/problemset/"
                     style="display:inline-block;background:#111;color:#fff;text-decoration:none;padding:9px 18px;border-radius:6px;font-size:13px;font-weight:600;margin-top:6px">
                    Solve on LeetCode →
                  </a>
                </td>
              </tr>
            </table>
          </td>
        </tr>
        <tr>
          <td style="padding:16px 28px;border-top:1px solid #f0f0f0">
            <p style="margin:0;font-size:12px;color:#999">
              Sent by <a href="${APP_URL}" style="color:#999">${APP_URL}</a>
            </p>
          </td>
        </tr>
      </table>
    </td></tr>
  </table>
</body>
</html>`;
}

function streakReminderText({ displayName, username, currentStreak }) {
  const name = displayName || username || "there";
  const streakLine =
    currentStreak > 0
      ? `Your current streak is ${currentStreak} day${currentStreak === 1 ? "" : "s"}. Solve one problem before midnight to keep it going!`
      : `Start a new streak today — solve one problem before midnight!`;

  return [
    `Hi ${name},`,
    ``,
    streakLine,
    ``,
    `https://leetcode.com/problemset/`,
    ``,
    `— leetcode-ted (${APP_URL})`,
  ].join("\n");
}

// ── Helpers ───────────────────────────────────────────────────────────────────

async function sendEmail({ to, subject, html, text }) {
  if (!FROM) throw new Error("SES_SENDER_EMAIL env var is not set");

  await ses.send(
    new SendEmailCommand({
      Source: FROM,
      Destination: { ToAddresses: [to] },
      Message: {
        Subject: { Data: subject, Charset: "UTF-8" },
        Body: {
          Html: { Data: html, Charset: "UTF-8" },
          Text: { Data: text, Charset: "UTF-8" },
        },
      },
    })
  );
  console.log(`Sent "${subject}" to ${to}`);
}

function escHtml(str) {
  return String(str ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}
