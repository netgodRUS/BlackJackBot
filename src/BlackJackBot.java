import com.fasterxml.jackson.databind.ObjectMapper;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup;
import com.pengrad.telegrambot.request.SendMessage;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;


public class BlackJackBot {

    private static HashMap<Long, Game> chats = new HashMap<>();
    static private String getBotToken() {
        return "";
    }
    public static void main(String[] args) {
        TelegramBot bot = new TelegramBot(getBotToken());
        bot.setUpdatesListener(updates -> {
            updates.forEach(upd -> {
                try {

                    long chatId = upd.message().chat().id();
                    String message = upd.message().text();

                    if (message.equals("@") || message.equals("Новая игра")) {
                        startNewGame(bot, chatId, chats);
                    } else if (message.equals("+") || message.equals("Еще")) {
                        Game game = chats.get(chatId);
                        if (game != null) {
                            game.addCard();
                        } else {
                            sendMessage(bot, chatId, "Игра еще не начата.\n" + showMenu());
                        }
                    } else if (message.equals("=") || message.equals("Хватит")) {
                        Game game = chats.get(chatId);
                        if (game != null) {
                            game.dealerTurn();
                        } else {
                            sendMessage(bot, chatId, "Игра еще не начата.\n" + showMenu());
                        }
                    } else if (message.equals("*") || message.equals("Меню")) {
                        sendMessage(bot, chatId, showMenu());
                    } else if (message.equals("start") || message.equals("старт")) {
                        sendMessage(bot, chatId, showWelcomeMessage());
                    } else {
                        sendMessage(bot, chatId, "Показать меню? (Нажми * или набери \"Меню\")");
                    }

                } catch (Exception e) {
                    System.out.println(e);
                }
            });
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });

    }

    private static void sendMessage(TelegramBot bot, long chatId, String message) {
        SendMessage request = new SendMessage(chatId, message);
        String[] buttons = new String[4];
        buttons[0] = "Новая игра";
        buttons[1] = "Еще";
        buttons[2] = "Хватит";
        buttons[3] = "Меню";

        request.replyMarkup(new ReplyKeyboardMarkup(buttons));
        bot.execute(request);
    }

    private static String showWelcomeMessage() {
        String str = "Приветствую тебя игрок.\n" +
                "Обойдемся без имен.\n" +
                "Тут ты можешь сыграть в БлэкДжек.\n" +
                "Да, кстати, дилер не любит проигрывать!\n" +
                showMenu();
        return str;
    }

    private static String showMenu() {
        String str = "Правила простые:\n" +
                "- все картинки 10;\n" +
                "- туз или 1 или 11;\n" +
                "- числа и есть числа;\n" +
                "- при равенстве сумм ты победил.\n" +
                "Команды:\n" +
                "- Для начала игры напиши \"Новая игра\" или @\n" +
                "- Если надо добавить карту шли \"Еще\" или +\n" +
                "- Если достаточно жми \"Хватит\" или =\n";
        return str;
    }

    private static void startNewGame(TelegramBot bot, long chatId, HashMap<Long, Game> chats) {
        Game currentGame;
        if (chats.containsKey(chatId)) {
            currentGame = chats.get(chatId);
        } else {
            currentGame = new Game(bot, chatId);
            chats.put(chatId, currentGame);
        }
        currentGame.makeDraw();
    }

}

class Game {

    private final TelegramBot bot;
    private final long chatId;
    private final String deck_id;
    private int cardsCount = 0;

    private final String clubs = new String(Character.toChars(0x2663));
    private final String diamonds = new String(Character.toChars(0x2666));
    private final String hearts = new String(Character.toChars(0x2665));
    private final String spades = new String(Character.toChars(0x2660));
    ArrayList<String> playerCards;
    ArrayList<String> dealerCards;

    public Game(TelegramBot bot, long chatId) {
        this.bot = bot;
        this.chatId = chatId;
        this.deck_id = receiveNewDeck();
    }

    public void makeDraw() {
        boolean playerDraw = true;
        resetDecks();
        String drawMessage = null;
        if (cardsCount > 30) shuffleCards();
        for (int i = 0; i < 4; i++) {
            String card = requestCard();
            if (playerDraw) {
                playerCards.add(card);
                drawMessage = "Карта игроку: " + showCard(card) + "\n";
            } else {
                dealerCards.add(card);
                drawMessage = "Карта дилеру: " + showCard(card) + "\n";
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            cardsCount++;
            playerDraw = !playerDraw;
            sendMessage(drawMessage);
        }
        showPlayerCards();
        showDealerCards();
        int playerSum = cardsSum(playerCards);
        int dealerSum = cardsSum(dealerCards);
        if (playerSum == 21) {
            showPlayerVictoryMessage();
        } else if ( dealerSum == 21) {
            showDealerVictoryMessage();
        } else if (playerSum > 21 || dealerSum > 21) {
            checkWinConditions(dealerSum, playerSum);
        }

    }

    private int cardsSum(ArrayList<String> playerCards) {
        int sum = 0;
        int aceCount = 0;
        for (String card : playerCards) {
            char ch = card.charAt(0);
            if (ch == '0' || ch == 'J' || ch == 'Q' || ch == 'K') {
                sum += 10;
            } else if (ch == 'A') {
                sum += 11;
                aceCount ++;
            } else {
                sum += Integer.parseInt(String.valueOf(ch));
            }
        }
        if (sum > 21 && aceCount > 0){
            for (int i = 0; i < aceCount; i++) {
                sum -= 10;
                if (sum <= 21) {
                    break;
                }
            }
        }

        return sum;
    }

    void sendMessage(String message) {
        SendMessage sendMessage = new SendMessage(chatId, message);
        bot.execute(sendMessage);
    }

    private void showPlayerVictoryMessage() {
        String str = "Поздравляем, игрок, ты победил.\n" +
                "Дилер хочет сказать тебе пару слов:\n" +
                "Дилер: " + getInsult() + "\n\n" +
                "Сыграть еще? (нажми Новая игра или @)";
        resetDecks();
        sendMessage(str);
    }


    private void showDealerVictoryMessage() {
        String str = "Ты проиграл. Такое бывает.\n" +
                "Сыграть еще? (нажми Новая игра или @)";
        resetDecks();
        sendMessage(str);
    }

    public void addCard() {
        if (playerCards.size() < 2 || dealerCards.size() < 2) {
            sendMessage("Игра не начата, показать меню *, начать новую игру @");
            return;
        }
        String newCard = requestCard();
        playerCards.add(newCard);
        showPlayerCards();
        if (cardsSum(playerCards) > 21) {
            showDealerVictoryMessage();
        }
    }

    public void dealerTurn() {
        if (playerCards.size() < 2 || dealerCards.size() < 2) {
            sendMessage("Игра не начата, показать меню *, начать новую игру @");
            return;
        }
        int dealerSum;
        while ((dealerSum = cardsSum(dealerCards)) <= 16) {
            String newCard = requestCard();
            dealerCards.add(newCard);
            showDealerCards();
        }
        int playerSum = cardsSum(playerCards);
        checkWinConditions(dealerSum, playerSum);

    }

    private void checkWinConditions(int dealerSum, int playerSum) {
        if (dealerSum > 21) {
            showPlayerVictoryMessage();
        } else {
            if (playerSum >= dealerSum) {
                showPlayerVictoryMessage();
            } else {
                showDealerVictoryMessage();
            }
        }
    }

    private void showDealerCards() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Карты дилера:   ");
        for (String card : dealerCards) {
            stringBuilder.append(showCard(card));
            stringBuilder.append("   ");
        }
        sendMessage(stringBuilder.toString());
    }

    private void showPlayerCards() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Карты игрока:   ");
        for (String card : playerCards) {
            stringBuilder.append(showCard(card));
            stringBuilder.append("   ");
        }
        sendMessage(stringBuilder.toString());
    }

    private String showCard(String card) {
        StringBuilder cardToDisplay = new StringBuilder();
        if (card.charAt(0) == '0') cardToDisplay.append("10");
        else cardToDisplay.append(card.charAt(0));

        if (card.charAt(1) == 'C') cardToDisplay.append(clubs);
        else if (card.charAt(1) == 'H') cardToDisplay.append(hearts);
        else if (card.charAt(1) == 'D') cardToDisplay.append(diamonds);
        else cardToDisplay.append(spades);
        return cardToDisplay.toString();
    }

    void shuffleCards() {
        String url = "https://deckofcardsapi.com/api/deck/" + getDeckId() + "/shuffle/";
        try {
            Jsoup.connect(url).ignoreContentType(true).execute();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    String requestCard() {
        String url = "https://deckofcardsapi.com/api/deck/" + getDeckId() + "/draw/?count=1";
        String jsonString = null;

        try {
            jsonString = Jsoup.connect(url).ignoreContentType(true).execute().body();
            ObjectMapper objectMapper = new ObjectMapper();
            var jsonNode = objectMapper.readTree(jsonString);
            var jsonNodeCards = jsonNode.get("cards");
            if (!jsonNodeCards.isEmpty()) {
                cardsCount++;
                return String.valueOf(jsonNodeCards.get(0).get("code")).replaceAll("\"", "");
            } else {
                throw new RuntimeException();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    String getDeckId() {
        return deck_id;
    }

    private void resetDecks() {
        dealerCards = new ArrayList<>();
        playerCards = new ArrayList<>();
    }

    String receiveNewDeck() {
        String url = "https://deckofcardsapi.com/api/deck/new/shuffle/?deck_count=1";
        String jsonString = null;
        try {
            jsonString = Jsoup.connect(url).ignoreContentType(true).execute().body();
            ObjectMapper objectMapper = new ObjectMapper();
            var jsonNode = objectMapper.readTree(jsonString);
            return String.valueOf(jsonNode.get("deck_id")).replaceAll("\"", "");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private String getInsult() {
        Random random = new Random();
        String url = "https://evilinsult.com/generate_insult.php?lang=ru&type=json&" + random.nextInt(1000);
        try {
            String jsonString = Jsoup.connect(url).ignoreContentType(true).execute().body();
            ObjectMapper objectMapper = new ObjectMapper();
            var jsonNode = objectMapper.readTree(jsonString);
            return String.valueOf(jsonNode.get("insult"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
